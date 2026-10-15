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
