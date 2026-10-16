package ru.styrk.app.sync

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONArray
import org.json.JSONObject
import ru.styrk.app.data.model.CurrentWorkoutState
import ru.styrk.app.data.model.MeasureType
import ru.styrk.app.ui.common.SetFormat
import ru.styrk.app.viewmodel.TrainingSessionViewModel
import java.time.ZoneId
import kotlin.coroutines.coroutineContext

/**
 * Тренер видит тренировку вживую.
 *
 * Пока идёт тренировка, её снимок уезжает на сервер — сразу после
 * правки и раз в десять секунд. Если у человека есть тренер с открытым
 * правом, тот видит тренировку у себя в кабинете и может вписать подход
 * за него. Вписанное приходит в ответ на снимок и добавляется так, будто
 * человек ввёл его сам.
 *
 * Тренер не пишет в тренировку напрямую — он просит, а вписывает
 * телефон. Так тренировку меняет только её хозяин, и двум правкам одного
 * и того же неоткуда взяться.
 *
 * Нет тренера — сервер отвечает «не храню», и телефон замолкает на пять
 * минут. Тренировка закончилась или отменена — один раз говорим «больше
 * нет». Запись задним числом не отправляется: её не с кем смотреть вживую.
 *
 * Работает, пока приложение открыто: крутится в MainScreen.
 *
 * Здесь же — два извещения: «начал» и «закончил». Их рассылает сервер
 * друзьям, которым человек включил «Сообщать о тренировках», и тренеру,
 * если выполняется назначенная им тренировка. Кому — решает сервер;
 * телефон только говорит, что случилось. См. [announce].
 */
object LiveSync {

    /** Подходы тренера, уже вписанные, — чтобы не вписать второй раз,
        если отметка «сделано» не дошла до сервера. */
    private val applied = HashSet<Long>()

    suspend fun run(context: Context, vm: TrainingSessionViewModel) {
        val prefs = SyncPrefs(context)
        val api = SyncApi(prefs)
        var lastSent: String? = null
        var lastAt = 0L
        var was = false
        var quietUntil = 0L

        while (coroutineContext.isActive) {
            val s = vm.state.value
            if (prefs.isSignedIn) {
                announce(context, api, s)
                flushFinish(context, api)
            }
            val active = prefs.isSignedIn && isRunning(s)
            val now = System.currentTimeMillis()

            if (active) {
                val snap = snapshot(s)
                val text = snap.toString()
                if (now >= quietUntil && (text != lastSent || now - lastAt >= 10_000)) {
                    when (val r = api.livePush(snap)) {
                        is SyncApi.Result.Ok -> {
                            lastSent = text; lastAt = now; was = true
                            if (!r.data.stored) quietUntil = now + 5 * 60_000
                            else apply(context, api, vm, r.data.ops)
                        }
                        is SyncApi.Result.Fail -> lastAt = now   // нет связи — попробуем позже
                    }
                }
            } else if (was && prefs.isSignedIn) {
                if (api.livePush(null) is SyncApi.Result.Ok) {
                    was = false; lastSent = null; quietUntil = 0L
                }
            } else if (!active) {
                quietUntil = 0L
            }
            delay(if (active) 2_000 else 5_000)
        }
    }

    // ── «начал» и «закончил» ─────────────────────────────────────────

    private const val EVENTS = "styrk_events"
    /** «Закончил», ещё не принятый сервером (R194). */
    private const val PENDING = "finish_pending"
    /** Не чаще раза в минуту, если сервер не ответил: не долбим его каждые две секунды. */
    private var nextTry = 0L

    /**
     * Сообщить серверу о начале и конце тренировки — по одному разу.
     *
     * Что уже сообщено, помним в настройках по моменту начала: приложение
     * могли закрыть посреди тренировки, и после возвращения «начал»
     * не должен уйти второй раз. Сервер тоже отсекает повтор, но лишний
     * запрос незачем и отправлять.
     *
     * «Начал» уходит, когда выбрано первое упражнение (или прошло три
     * минуты): к этому моменту известна группа мышц, а заглянувший
     * в тренировку и тут же вышедший никого не потревожит.
     *
     * «Закончил» — см. [queueFinish]: ставится в очередь в момент
     * сохранения и уходит, как только будет связь. Отменённая
     * тренировка не сообщается: сообщать нечего. Запись задним числом
     * не сообщается вовсе.
     */
    private suspend fun announce(context: Context, api: SyncApi, s: CurrentWorkoutState) {
        if (!s.trainingStarted || s.isBackdated) return
        val now = System.currentTimeMillis()
        if (now < nextTry) return
        val startedAt = s.workoutStartTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (now - startedAt > 86_400_000L) return
        val ev = context.getSharedPreferences(EVENTS, Context.MODE_PRIVATE)
        val day = s.workoutStartTime.toLocalDate().toEpochDay()
        val group = s.selectedExercises.mapNotNull { it.muscleGroup?.title }.distinct().joinToString(", ")

        if (isRunning(s) && ev.getLong("start", 0L) != startedAt &&
            (s.selectedExercises.isNotEmpty() || now - startedAt > 3 * 60_000L)) {
            // Тренировка идёт уже два часа (приложение обновили посреди неё) —
            // «начал» сейчас было бы враньём. Отмечаем как сказанное и молчим.
            if (now - startedAt > 2 * 3_600_000L) {
                ev.edit().putLong("start", startedAt).apply()
            } else {
                val r = api.workoutEvent("start", startedAt, day, group, s.selectedExercises.map { it.name })
                if (r is SyncApi.Result.Ok) ev.edit().putLong("start", startedAt).apply()
                else nextTry = now + 60_000L
            }
        }

        // Запасной путь: экран итога открыт, а в очередь «закончил»
        // почему-то не встал.
        if (s.currentScreen == "summary") queueFinish(context, s)
    }

    /**
     * Поставить «закончил» в очередь — в момент сохранения тренировки
     * (MainScreen, по сигналу workoutSaved).
     *
     * ИСПРАВЛЕНО (R194). Раньше «закончил» уходил только пока открыт
     * экран итога, прямо из цикла. После долгого сна телефона первая
     * попытка часто не проходила — сеть ещё не поднялась, — следующая
     * была через минуту, а итог к тому времени уже закрыли. Тренировка
     * сохранялась и уезжала на сервер, а тренеру ничего не приходило.
     * Теперь извещение лежит в настройках, пока сервер его не примет:
     * переживёт и закрытый итог, и перезапуск приложения.
     */
    fun queueFinish(context: Context, s: CurrentWorkoutState) {
        // Только с экрана итога: сигнал workoutSaved повторяется при
        // пересоздании экрана (поворот), и к тому времени может идти
        // уже следующая тренировка — её «закончил» было бы враньём.
        if (!s.trainingStarted || s.isBackdated || s.currentScreen != "summary") return
        val ev = context.getSharedPreferences(EVENTS, Context.MODE_PRIVATE)
        val startedAt = s.workoutStartTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (ev.getLong("finish", 0L) == startedAt) return
        val old = ev.getString(PENDING, null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (old?.optLong("startedAt") == startedAt) return
        val done = s.selectedExercises.filter { it.sets.isNotEmpty() }
        val names = JSONArray(); done.forEach { names.put(it.name) }
        val job = JSONObject()
            .put("startedAt", startedAt)
            .put("day", s.workoutStartTime.toLocalDate().toEpochDay())
            .put("group", s.selectedExercises.mapNotNull { it.muscleGroup?.title }.distinct().joinToString(", "))
            .put("names", names)
            .put("minutes", ((System.currentTimeMillis() - startedAt) / 60_000L).toInt())
            .put("exercises", done.size)
            .put("sets", done.sumOf { it.sets.size })
        ev.edit().putString(PENDING, job.toString()).apply()
        nextTry = 0L   // отправить сразу, не дожидаясь паузы после старых неудач
    }

    /** Отправить «закончил» из очереди. Нет связи — останется до следующей попытки. */
    suspend fun flushFinish(context: Context, api: SyncApi) {
        val ev = context.getSharedPreferences(EVENTS, Context.MODE_PRIVATE)
        val raw = ev.getString(PENDING, null) ?: return
        val now = System.currentTimeMillis()
        if (now < nextTry) return
        val job = runCatching { JSONObject(raw) }.getOrNull()
        val startedAt = job?.optLong("startedAt") ?: 0L
        // Сервер не принимает извещения старше суток — такое уже не новость.
        if (job == null || now - startedAt > 86_400_000L) {
            ev.edit().remove(PENDING).apply(); return
        }
        val names = job.optJSONArray("names")
        val r = api.workoutEvent(
            "finish", startedAt, job.optLong("day"), job.optString("group"),
            List(names?.length() ?: 0) { names!!.optString(it) },
            minutes = job.optInt("minutes"), exercises = job.optInt("exercises"), sets = job.optInt("sets")
        )
        // Ответ 400 — сервер счёл извещение неверным; повтор ничего не изменит.
        val final = r is SyncApi.Result.Ok || (r is SyncApi.Result.Fail && r.code == 400)
        if (final) {
            val e = ev.edit().putLong("finish", startedAt)
            // Пока шёл запрос, в очередь могла встать следующая тренировка.
            if (ev.getString(PENDING, null) == raw) e.remove(PENDING)
            e.apply()
        } else nextTry = now + 60_000L
    }

    /** Идёт ли настоящая тренировка — не запись задним числом и не итог. */
    private fun isRunning(s: CurrentWorkoutState): Boolean =
        s.trainingStarted && !s.isBackdated &&
                s.currentScreen != "start" && s.currentScreen != "summary"

    private fun snapshot(s: CurrentWorkoutState): JSONObject {
        val items = JSONArray()
        s.selectedExercises.forEach { e ->
            val sets = JSONArray()
            e.sets.forEach { x ->
                sets.put(JSONObject()
                    .put("weight", x.weight.toDouble())
                    .put("reps", x.reps)
                    .putOpt("seconds", x.seconds)
                    .putOpt("distanceKm", x.distanceKm?.toDouble())
                    .putOpt("load", x.load?.toDouble())
                    .putOpt("speed", x.speed?.toDouble()))
            }
            items.put(JSONObject().put("name", e.name).put("type", e.measureType.name).put("sets", sets))
        }
        return JSONObject()
            .put("startedAt", s.workoutStartTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            .putOpt("weight", s.athleteWeight?.toDouble())
            .put("items", items)
    }

    private suspend fun apply(
        context: Context, api: SyncApi, vm: TrainingSessionViewModel, ops: List<SyncApi.LiveOp>
    ) {
        val fresh = ops.filter { it.id !in applied }
        val said = ArrayList<String>()
        fresh.forEach { op ->
            val type = MeasureType.entries.find { it.name == op.type } ?: MeasureType.WEIGHT_REPS
            if (vm.applyCoachSet(op.exercise, type, op.set)) {
                applied += op.id
                said += op.exercise + " — " + SetFormat.value(type, op.set)
            }
        }
        val done = ops.map { it.id }.filter { it in applied }
        if (done.isNotEmpty()) api.liveOpsDone(done)
        if (said.isNotEmpty()) {
            Toast.makeText(context, "Тренер записал: " + said.joinToString(" · "),
                Toast.LENGTH_LONG).show()
        }
    }
}
