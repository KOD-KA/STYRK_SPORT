package ru.styrk.app.sync

import ru.styrk.app.data.model.PairedExercises
import ru.styrk.app.data.model.ExerciseNotes
import ru.styrk.app.data.model.ExerciseTarget

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.styrk.app.data.local.database.WorkoutDatabase
import ru.styrk.app.data.local.entity.*
import ru.styrk.app.ui.common.ExercisePhoto

/**
 * Обмен данными с сервером.
 *
 * Как это работает:
 *
 *  1. Собираем всё, что менялось на телефоне и ещё не уехало (dirty = 1),
 *     плюс отметки об удалении.
 *  2. Отправляем одним запросом и в том же ответе получаем то, что
 *     менялось на сервере с прошлого раза.
 *  3. Раскладываем полученное по своим таблицам.
 *  4. Снимаем отметку «не отправлено» и стираем отметки об удалении.
 *
 * Спор решается по времени правки: кто изменил позже, тот и прав.
 * Проверку делает сервер, здесь она не дублируется.
 *
 * Рекорды не передаются вовсе. Они целиком выводятся из тренировок,
 * поэтому после приёма чужих тренировок пересчитываются заново. Так они
 * не могут разойтись с архивом — а разошлись бы обязательно, будь они
 * вторым источником правды.
 */
/**
 * «С сервера приехали изменения — перечитайте базу».
 *
 * Раньше экран перечитывал себя только после обмена при возвращении
 * в приложение. Обмены бывают и в другие моменты — после сохранения
 * тренировки, из окна аккаунта, — и тогда приехавший план ложился
 * в базу, а на экране его не было до перезапуска: календарь пустой,
 * кнопки «начать запланированную» нет.
 */
object DataChanged {
    var version by androidx.compose.runtime.mutableIntStateOf(0)
        private set
    fun bump() { version++ }
}

class SyncManager(context: Context, private val db: WorkoutDatabase) {

    // Нужна внутренняя память под фото профиля. Берём application —
    // менеджер живёт дольше экрана, и держать ссылку на экран нельзя.
    private val filesDir = context.applicationContext.filesDir
    private val appCtx = context.applicationContext
    private val prefs = SyncPrefs(context)
    private val api = SyncApi(prefs)

    /**
     * Как поступить, если данные есть и на телефоне, и на сервере.
     *
     * MERGE — обычный обмен: каждая запись отдельно, спор решается по
     *   времени правки. Подходит почти всегда и ничего не теряет.
     *
     * PUSH_OVERWRITE — на сервере станет ровно то, что на телефоне.
     *   Всё лишнее на сервере удаляется. Нужно, когда телефон — источник
     *   правды, а на сервере накопился мусор.
     *
     * PULL_REPLACE — на телефоне станет ровно то, что на сервере.
     *   Локальные данные стираются. Нужно на новом устройстве или когда
     *   на телефоне что-то испортилось.
     *
     * Два последних способа необратимы, поэтому в приложении они спрятаны
     * за отдельным подтверждением и никогда не выполняются сами.
     */
    enum class Mode { MERGE, PUSH_OVERWRITE, PULL_REPLACE }

    /**
     * Что лежит по обе стороны перед обменом.
     * onlyHere / onlyThere считаются ПО СОДЕРЖИМОМУ, а не по опознавательным
     * знакам: одна и та же тренировка, записанная на двух устройствах,
     * имеет разные знаки, но одинаковое содержимое.
     */
    data class Inspection(
        val here: Int,
        val there: Int,
        val onlyHere: Int,
        val onlyThere: Int,
        val failed: Boolean = false
    ) {
        /** Обе стороны знают то, чего не знает другая — надо спросить человека. */
        val conflicting: Boolean get() = !failed && onlyHere > 0 && onlyThere > 0
    }

    sealed class Outcome {
        /** Аккаунта нет — это не ошибка, приложение так и задумано. */
        data object NotSignedIn : Outcome()
        data class Ok(val sent: Int, val received: Int) : Outcome()
        data class Fail(val message: String) : Outcome()
    }

    suspend fun sync(mode: Mode = Mode.MERGE): Outcome = withContext(Dispatchers.IO) {
        if (!prefs.isSignedIn) return@withContext Outcome.NotSignedIn

        try {
            when (mode) {
                Mode.PULL_REPLACE -> {
                    // Стираем своё и просим историю с самого начала
                    wipeLocal()
                    prefs.forgetSyncPoint()
                }
                Mode.PUSH_OVERWRITE -> {
                    // Отмечаем к удалению всё, чего на телефоне нет,
                    // и делаем свои записи заведомо более свежими
                    val extras = serverItemsNotHere()
                    if (extras == null) return@withContext Outcome.Fail("Сервер не отвечает")
                    extras.forEach { (kind, uuid) ->
                        db.syncTombstoneDao().put(SyncTombstoneEntity(kind = kind, uuid = uuid))
                    }
                    touchEverything()
                    prefs.forgetSyncPoint()
                }
                Mode.MERGE -> Unit
            }

            val items = JSONArray()
            val sentWorkouts = mutableListOf<String>()
            val sentPlanned = mutableListOf<String>()
            val sentCustom = mutableListOf<String>()
            val sentMy = mutableListOf<String>()
            val sentMetrics = mutableListOf<String>()
            var sentProfile = false
            val sentNotes = mutableListOf<ExerciseNotes.Note>()
            val sentTombstones = mutableListOf<Pair<String, String>>()

            // Что и с каким временем правки уехало. Время нужно, чтобы
            // отличить своё же эхо от чужой записи — см. приём ниже.
            val sentStamp = HashMap<Pair<String, String>, Long>()
            fun stamp(kind: String, uuid: String, updatedAt: Long) {
                sentStamp[kind to uuid] = updatedAt
            }

            // ── что уехало ────────────────────────────────────────────
            db.workoutHistoryDao().getDirty().forEach { w ->
                val payload = workoutPayload(w)

                /* Тренировку без подходов не отправляем — вторая застава.
                 *
                 * Приложение таких не создаёт: завершить тренировку,
                 * не записав ни одного подхода, оно не даёт. Значит
                 * пустая тренировка здесь — не запись человека, а мы
                 * сами, заставшие её на полпути.
                 *
                 * Именно так и терялись подходы: обмен успевал схватить
                 * тренировку между её появлением в базе и записью
                 * подходов, отправлял пустую и снимал пометку «надо
                 * отправить». Больше её никто не трогал, и на сервере
                 * она оставалась пустой навсегда.
                 *
                 * Сама щель уже закрыта — запись стала неделимой. Эта
                 * застава на случай, если щель однажды появится снова:
                 * пометку не снимаем, и тренировка уедет следующим
                 * обменом, когда подходы будут на месте.
                 */
                if ((payload.optJSONArray("sets")?.length() ?: 0) == 0) return@forEach

                items.put(envelope(SyncKind.WORKOUT, w.uuid, w.updatedAt, payload))
                sentWorkouts += w.uuid
                stamp(SyncKind.WORKOUT, w.uuid, w.updatedAt)
            }
            db.plannedWorkoutDao().getDirty().forEach { p ->
                items.put(envelope(SyncKind.PLANNED, p.uuid, p.updatedAt, plannedPayload(p)))
                sentPlanned += p.uuid
                stamp(SyncKind.PLANNED, p.uuid, p.updatedAt)
            }
            db.customExerciseDao().getDirty().forEach { c ->
                items.put(envelope(SyncKind.CUSTOM_EXERCISE, c.uuid, c.updatedAt, JSONObject()
                    .put("name", c.name)
                    .put("muscleGroup", c.muscleGroup)
                    .putOpt("measureType", c.measureType)
                    // Описание и снимок кладём ВСЕГДА, пустые — явным null.
                    //
                    // Раньше пустое поле просто не клали, и «убрал снимок»
                    // было не отличить от «про снимок ничего не знаю».
                    // Отличать надо: без этого снимок, убранный здесь,
                    // возвращался с другого устройства при первом же обмене.
                    //
                    // Отсутствие поля осталось означать «не знаю» — так шлют
                    // старые версии, и отбирать у них снимки не за что.
                    .put("description", c.description ?: JSONObject.NULL)
                    // Снимок едет содержимым, а не путём: путь с чужого
                    // устройства бессмыслен, а веб-версия про файлы
                    // не знает вовсе. Формат тот же, что у фото профиля.
                    .put("photo", ExercisePhoto.encode(c.photoPath) ?: JSONObject.NULL)))
                sentCustom += c.uuid
                stamp(SyncKind.CUSTOM_EXERCISE, c.uuid, c.updatedAt)
            }
            db.myWorkoutDao().getDirty().forEach { m ->
                items.put(envelope(SyncKind.MY_WORKOUT, m.uuid, m.updatedAt, myWorkoutPayload(m)))
                sentMy += m.uuid
                stamp(SyncKind.MY_WORKOUT, m.uuid, m.updatedAt)
            }
            db.bodyMetricDao().getDirty().forEach { m ->
                items.put(envelope(SyncKind.BODY_METRIC, m.uuid, m.updatedAt, metricPayload(m)))
                sentMetrics += m.uuid
                stamp(SyncKind.BODY_METRIC, m.uuid, m.updatedAt)
            }
            db.userProfileDao().getDirty().forEach { p ->
                val payload = JSONObject()
                    .putOpt("name", p.name)
                    .putOpt("heightCm", p.heightCm?.toDouble())
                    .putOpt("weightKg", p.weightKg?.toDouble())
                    /* Пол кладём только когда он выбран.
                       putOpt с null поля не добавит — и это ровно то,
                       что нужно: телефон, где пол ещё не спрашивали,
                       не должен стирать выбор, сделанный в браузере. */
                    .putOpt("sex", p.sex)
                // Фото едет картинкой, а не путём — см. ProfilePhoto.
                //
                // Если фото на телефоне нет, поле НЕ отправляется вовсе.
                // Это не то же самое, что отправить пустоту: пустота
                // означала бы «убрать фото», и телефон без снимка стирал бы
                // фото, поставленное в браузере, при первом же обмене.
                // Отсутствие поля означает «мне нечего сказать про фото».
                ProfilePhoto.encode(p.photoPath)?.let { payload.put("photo", it) }
                items.put(envelope(SyncKind.PROFILE, "me", p.updatedAt, payload))
                sentProfile = true
                stamp(SyncKind.PROFILE, "me", p.updatedAt)
            }
            // Свои заметки к упражнениям. Пустой текст едет как есть —
            // это «заметку убрали», и убрать её надо и на сервере.
            ExerciseNotes.dirty(appCtx).forEach { n ->
                val u = ExerciseNotes.uuidOf(n.name)
                items.put(envelope(ExerciseNotes.KIND, u, n.updatedAt, JSONObject()
                    .put("name", n.name).put("text", n.text)))
                sentNotes += n
                stamp(ExerciseNotes.KIND, u, n.updatedAt)
            }
            db.syncTombstoneDao().getAll().forEach { t ->
                items.put(JSONObject()
                    .put("kind", t.kind)
                    .put("uuid", t.uuid)
                    .put("updatedAt", t.updatedAt)
                    .put("deleted", true))
                sentTombstones += t.kind to t.uuid
                stamp(t.kind, t.uuid, t.updatedAt)
            }

            val body = JSONObject()
                .put("since", prefs.lastSync)
                .put("items", items)

            val response = when (val r = api.sync(body)) {
                is SyncApi.Result.Ok -> r.data
                is SyncApi.Result.Fail -> {
                    if (r.unauthorized) {
                        // Токен погашен или аккаунт удалён с другого
                        // устройства. Выходим, но НИЧЕГО не удаляем:
                        // дневник продолжает работать как без аккаунта.
                        prefs.signOut()
                    }
                    return@withContext Outcome.Fail(r.message)
                }
            }

            // ── что приехало ──────────────────────────────────────────
            val incoming = response.optJSONArray("items") ?: JSONArray()
            var applied = 0
            var workoutsTouched = false
            for (i in 0 until incoming.length()) {
                val item = incoming.optJSONObject(i) ?: continue
                val kind = item.str("kind")
                val uuid = item.str("uuid")
                if (kind.isBlank() || uuid.isBlank()) continue

                val deleted = item.optBoolean("deleted")
                val updatedAt = item.optLong("updatedAt")
                val payload = item.optJSONObject("payload")

                // ЭХО ИЛИ ЧУЖОЕ.
                //
                // Раньше пропускалось всё, что мы только что отправили.
                // Это неверно: сервер отклоняет нашу запись, если у него
                // лежит более свежая, и возвращает в ответе именно её.
                // Пропуская «своё», телефон выбрасывал чужую правку —
                // и расхождение оставалось навсегда, потому что второй раз
                // сервер её уже не пришлёт (отметка времени уйдёт вперёд).
                //
                // Отличаем по времени правки. У сервера по каждой записи
                // лежит ровно одна версия, поэтому вариантов всего два:
                //   пришло СВЕЖЕЕ отправленного — победила другая
                //     сторона, принимаем (здесь и приезжает фотография,
                //     поставленная в браузере);
                //   пришло такое же или старее — это наше эхо. Старее оно
                //     бывает, когда часы телефона убежали вперёд: сервер
                //     подрезает время до «сейчас плюс минута».
                val sentAt = sentStamp[kind to uuid]
                if (sentAt != null && updatedAt <= sentAt) continue

                // При перезаписи сервера принимать нечего: телефон здесь
                // источник правды, и всё пришедшее — то, что мы только что
                // затёрли собственными данными.
                if (mode == Mode.PUSH_OVERWRITE) continue

                if (kind == SyncKind.WORKOUT) workoutsTouched = true
                if (deleted) applyDelete(kind, uuid) else applyUpsert(kind, uuid, updatedAt, payload)
                applied++
            }

            // ── прибираем за собой ────────────────────────────────────
            if (sentWorkouts.isNotEmpty()) db.workoutHistoryDao().clearDirty(sentWorkouts)
            if (sentPlanned.isNotEmpty()) db.plannedWorkoutDao().clearDirty(sentPlanned)
            if (sentCustom.isNotEmpty()) db.customExerciseDao().clearDirty(sentCustom)
            if (sentMy.isNotEmpty()) db.myWorkoutDao().clearDirty(sentMy)
            if (sentMetrics.isNotEmpty()) db.bodyMetricDao().clearDirty(sentMetrics)
            if (sentProfile) db.userProfileDao().clearDirty()
            sentNotes.forEach { ExerciseNotes.sent(appCtx, it.name, it.updatedAt) }
            sentTombstones.forEach { (k, u) -> db.syncTombstoneDao().remove(k, u) }

            // Одинаковые тренировки, приехавшие с разных устройств,
            // склеиваем ДО пересчёта рекордов — иначе рекорд посчитается
            // по задвоенным подходам.
            if (mode != Mode.PUSH_OVERWRITE) {
                val merged = dedupeAll()
                if (merged > 0) workoutsTouched = true
            }

            if (workoutsTouched) rebuildRecords()

            // Время берём серверное: часы телефона могут врать, и тогда
            // часть изменений либо приезжала бы дважды, либо терялась.
            prefs.lastSync = response.optLong("serverTime", prefs.lastSync)

            // Приехало чужое — пусть экраны перечитают себя. Сигнал общий
            // для всех обменов: и при возвращении в приложение, и после
            // сохранения тренировки, и из окна аккаунта.
            if (applied > 0) DataChanged.bump()
            Outcome.Ok(sent = items.length(), received = applied)
        } catch (e: Exception) {
            Outcome.Fail("Не удалось обменяться данными")
        }
    }

    // ═══════════════════ ОДИНАКОВЫЕ ТРЕНИРОВКИ ═══════════════════

    /**
     * Отпечаток тренировки по её содержимому.
     *
     * Опознавательный знак (uuid) для этого не годится: он рождается на
     * устройстве, и одна и та же тренировка, попавшая на два телефона
     * из одного бэкапа, получит два разных знака. Сервер честно сохранит
     * обе — он про их содержимое ничего не знает.
     *
     * Время округляем до минуты: секунды у копий могут разойтись,
     * а тренировка, начатая в ту же минуту с тем же набором подходов, —
     * это одна и та же тренировка.
     */
    private fun fingerprintOf(
        dateMs: Long, duration: Long?, muscleGroup: String,
        exercises: List<String>, sets: List<String>
    ): String = buildString {
        append(dateMs / 60_000); append('|')
        append(duration ?: -1); append('|')
        append(muscleGroup); append('|')
        append(exercises.joinToString(",")); append('|')
        append(sets.joinToString(","))
    }

    private suspend fun localFingerprint(w: CompletedWorkoutEntity): String {
        val ex = db.completedWorkoutExerciseDao().getByWorkout(w.id)
            .map { it.exerciseName + ":" + (it.measureType ?: "") }
        val st = db.completedWorkoutSetDao().getByWorkout(w.id)
            .map {
                it.exerciseName + ":" + it.weight + ":" + it.reps + ":" +
                        (it.seconds ?: "") + ":" + (it.distanceKm ?: "") + ":" +
                        (it.load ?: "") + ":" + (it.speed ?: "")
            }
        return fingerprintOf(w.date, w.duration, w.muscleGroup, ex, st)
    }

    private fun payloadFingerprint(p: JSONObject): String {
        val exArr = p.optJSONArray("exercises") ?: JSONArray()
        val ex = (0 until exArr.length()).map {
            val o = exArr.optJSONObject(it)
            (o?.str("name") ?: "") + ":" + (o?.str("measureType") ?: "")
        }
        val stArr = p.optJSONArray("sets") ?: JSONArray()
        val st = (0 until stArr.length()).map {
            val o = stArr.optJSONObject(it) ?: JSONObject()
            o.str("exerciseName") + ":" +
                    o.optDouble("weight", 0.0).toFloat() + ":" + o.optInt("reps") + ":" +
                    (if (o.isNull("seconds")) "" else o.optInt("seconds").toString()) + ":" +
                    (if (o.isNull("distanceKm")) "" else o.optDouble("distanceKm").toFloat().toString()) + ":" +
                    (if (o.isNull("load")) "" else o.optDouble("load").toFloat().toString()) + ":" +
                    (if (o.isNull("speed")) "" else o.optDouble("speed").toFloat().toString())
        }
        val duration = if (p.isNull("duration")) null else p.optLong("duration")
        return fingerprintOf(p.optLong("date"), duration, p.str("muscleGroup"), ex, st)
    }

    /**
     * Склеить одинаковые тренировки.
     *
     * Из каждой группы совпадающих оставляем ту, чей опознавательный знак
     * меньше по алфавиту. Правило нарочно тупое и одинаковое на всех
     * устройствах: тогда телефон и компьютер независимо приходят к одному
     * и тому же ответу, и склейка не ходит по кругу.
     *
     * Лишние не просто удаляются, а помечаются удалёнными — иначе они
     * вернулись бы с сервера при следующем обмене.
     */
    /**
     * Склейка двойников — всех видов, а не только тренировок.
     *
     * Одна и та же запись, попавшая в дневник дважды с разных сторон,
     * получает два разных опознавательных знака: знак рождается
     * на устройстве, и сервер честно хранит обе — про их содержимое
     * он ничего не знает.
     *
     * Так бывает не от поломки, а от обычной жизни: дневник вели
     * и на телефоне, и в браузере, а потом завели один аккаунт. Или
     * переставили приложение и восстановились из бэкапа.
     *
     * Тренировки склеивались давно, остальное — нет, и человек видел
     * два одинаковых своих упражнения, два плана на один час и два
     * замера за один день. Правило одно на все виды: остаётся запись
     * с меньшим знаком, лишние помечаются удалёнными — чтобы двойники
     * ушли и с сервера, а не вернулись при следующем обмене.
     */
    private suspend fun dedupeAll(): Int =
        dedupeWorkouts() + dedupeCustom() + dedupePlanned() +
            dedupeMetrics() + dedupeMy()

    /** Своё упражнение — по названию: одно и то же имя дважды не нужно. */
    private suspend fun dedupeCustom(): Int {
        val all = db.customExerciseDao().getAll()
        if (all.size < 2) return 0
        var removed = 0
        all.groupBy { it.name.trim().lowercase() }.values.forEach { copies ->
            if (copies.size < 2) return@forEach
            val keep = copies.minByOrNull { it.uuid } ?: return@forEach
            /* Описание и снимок могли остаться на том двойнике, который
               уходит. Забираем их себе: иначе склейка не убирает лишнее,
               а теряет нужное. */
            if (keep.description.isNullOrBlank() && keep.photoPath.isNullOrBlank()) {
                val donor = copies.firstOrNull {
                    it.uuid != keep.uuid &&
                        (!it.description.isNullOrBlank() || !it.photoPath.isNullOrBlank())
                }
                if (donor != null) {
                    db.customExerciseDao().setDetails(
                        keep.id, donor.description, donor.photoPath, System.currentTimeMillis()
                    )
                }
            }
            copies.filter { it.uuid != keep.uuid }.forEach { extra ->
                db.syncTombstoneDao().put(
                    SyncTombstoneEntity(kind = SyncKind.CUSTOM_EXERCISE, uuid = extra.uuid)
                )
                db.customExerciseDao().deleteById(extra.id)
                removed++
            }
        }
        return removed
    }

    /** План — по дню, часу и группе: то же занятие в тот же час. */
    private suspend fun dedupePlanned(): Int {
        val all = db.plannedWorkoutDao().getAll()
        if (all.size < 2) return 0
        var removed = 0
        all.groupBy {
            listOf(it.date, it.hour, it.minute, it.muscleGroup.trim().lowercase()).joinToString("|")
        }.values.forEach { copies ->
            if (copies.size < 2) return@forEach
            val keep = copies.minByOrNull { it.uuid } ?: return@forEach
            copies.filter { it.uuid != keep.uuid }.forEach { extra ->
                db.syncTombstoneDao().put(
                    SyncTombstoneEntity(kind = SyncKind.PLANNED, uuid = extra.uuid)
                )
                db.plannedWorkoutDao().deleteExercisesByWorkout(extra.id)
                db.plannedWorkoutDao().deleteById(extra.id)
                removed++
            }
        }
        return removed
    }

    /** Замер — по дню: на весы встают раз в день, а не дважды. */
    private suspend fun dedupeMetrics(): Int {
        val all = db.bodyMetricDao().getAll()
        if (all.size < 2) return 0
        var removed = 0
        all.groupBy { it.date }.values.forEach { copies ->
            if (copies.size < 2) return@forEach
            val keep = copies.minByOrNull { it.uuid } ?: return@forEach
            copies.filter { it.uuid != keep.uuid }.forEach { extra ->
                db.syncTombstoneDao().put(
                    SyncTombstoneEntity(kind = SyncKind.BODY_METRIC, uuid = extra.uuid)
                )
                db.bodyMetricDao().deleteById(extra.id)
                removed++
            }
        }
        return removed
    }

    /** Образец — по названию. */
    private suspend fun dedupeMy(): Int {
        val all = db.myWorkoutDao().getAll()
        if (all.size < 2) return 0
        var removed = 0
        all.groupBy { it.title.trim().lowercase() }.values.forEach { copies ->
            if (copies.size < 2) return@forEach
            val keep = copies.minByOrNull { it.uuid } ?: return@forEach
            copies.filter { it.uuid != keep.uuid }.forEach { extra ->
                db.syncTombstoneDao().put(
                    SyncTombstoneEntity(kind = SyncKind.MY_WORKOUT, uuid = extra.uuid)
                )
                db.myWorkoutDao().deleteExercises(extra.id)
                db.myWorkoutDao().deleteById(extra.id)
                removed++
            }
        }
        return removed
    }

    private suspend fun dedupeWorkouts(): Int {
        val all = db.workoutHistoryDao().getAllWorkouts()
        if (all.size < 2) return 0

        val groups = HashMap<String, MutableList<CompletedWorkoutEntity>>()
        all.forEach { w -> groups.getOrPut(localFingerprint(w)) { mutableListOf() }.add(w) }

        var removed = 0
        groups.values.forEach { copies ->
            if (copies.size < 2) return@forEach
            val keep = copies.minByOrNull { it.uuid } ?: return@forEach
            copies.filter { it.uuid != keep.uuid }.forEach { extra ->
                db.syncTombstoneDao().put(
                    SyncTombstoneEntity(kind = SyncKind.WORKOUT, uuid = extra.uuid)
                )
                db.completedWorkoutExerciseDao().deleteByWorkout(extra.id)
                db.completedWorkoutSetDao().deleteByWorkout(extra.id)
                db.workoutHistoryDao().deleteById(extra.id)
                removed++
            }
        }
        return removed
    }

    // ═══════════════════ РАЗВЕДКА ПЕРЕД ОБМЕНОМ ═══════════════════

    /**
     * Посмотреть, что есть по обе стороны, ничего не меняя.
     *
     * Нужно, чтобы не сливать молча две разные истории тренировок:
     * если и там и там есть своё, человек должен решить сам.
     */
    suspend fun inspect(): Inspection = withContext(Dispatchers.IO) {
        if (!prefs.isSignedIn) return@withContext Inspection(0, 0, 0, 0, failed = true)
        try {
            val probe = JSONObject().put("since", 0).put("items", JSONArray())
            val response = when (val r = api.sync(probe)) {
                is SyncApi.Result.Ok -> r.data
                is SyncApi.Result.Fail -> return@withContext Inspection(0, 0, 0, 0, failed = true)
            }

            val localPrints = db.workoutHistoryDao().getAllWorkouts()
                .map { localFingerprint(it) }.toSet()

            val serverPrints = mutableSetOf<String>()
            val items = response.optJSONArray("items") ?: JSONArray()
            for (i in 0 until items.length()) {
                val it = items.optJSONObject(i) ?: continue
                if (it.str("kind") != SyncKind.WORKOUT) continue
                if (it.optBoolean("deleted")) continue
                val p = it.optJSONObject("payload") ?: continue
                serverPrints += payloadFingerprint(p)
            }

            Inspection(
                here = localPrints.size,
                there = serverPrints.size,
                onlyHere = (localPrints - serverPrints).size,
                onlyThere = (serverPrints - localPrints).size
            )
        } catch (e: Exception) {
            Inspection(0, 0, 0, 0, failed = true)
        }
    }

    // ═══════════════════ ТРИ СПОСОБА ═══════════════════

    /** Что лежит на сервере, чего нет на телефоне. null = связи нет. */
    private suspend fun serverItemsNotHere(): List<Pair<String, String>>? {
        val probe = JSONObject().put("since", 0).put("items", JSONArray())
        val response = when (val r = api.sync(probe)) {
            is SyncApi.Result.Ok -> r.data
            is SyncApi.Result.Fail -> return null
        }
        val here = buildSet {
            db.workoutHistoryDao().getAllWorkouts().forEach { add(SyncKind.WORKOUT to it.uuid) }
            db.plannedWorkoutDao().getAll().forEach { add(SyncKind.PLANNED to it.uuid) }
            db.customExerciseDao().getAll().forEach { add(SyncKind.CUSTOM_EXERCISE to it.uuid) }
            db.bodyMetricDao().getAll().forEach { add(SyncKind.BODY_METRIC to it.uuid) }
            db.userProfileDao().get()?.let { add(SyncKind.PROFILE to "me") }
            // Образцы тренировок здесь раньше не числились — и перезапись
            // сервера стирала там все образцы, хотя на телефоне они были.
            db.myWorkoutDao().getAll().forEach { add(SyncKind.MY_WORKOUT to it.uuid) }
            ExerciseNotes.all(appCtx).forEach { add(ExerciseNotes.KIND to ExerciseNotes.uuidOf(it.name)) }
        }
        val items = response.optJSONArray("items") ?: JSONArray()
        val extras = mutableListOf<Pair<String, String>>()
        for (i in 0 until items.length()) {
            val it = items.optJSONObject(i) ?: continue
            if (it.optBoolean("deleted")) continue
            val pair = it.str("kind") to it.str("uuid")
            if (pair.first.isNotBlank() && pair.second.isNotBlank() && pair !in here) extras += pair
        }
        return extras
    }

    /**
     * Пометить всё своё изменённым прямо сейчас.
     *
     * Нужно для перезаписи сервера: спор решается по времени правки,
     * и без этого старая местная запись проиграла бы более свежей
     * серверной — то есть перезапись не состоялась бы.
     */
    private suspend fun touchEverything() {
        val now = System.currentTimeMillis()
        db.workoutHistoryDao().getAllWorkouts().forEach {
            db.workoutHistoryDao().upsert(it.copy(updatedAt = now, dirty = 1))
        }
        db.plannedWorkoutDao().getAll().forEach {
            db.plannedWorkoutDao().upsert(it.copy(updatedAt = now, dirty = 1))
        }
        db.customExerciseDao().getAll().forEach {
            db.customExerciseDao().upsert(it.copy(updatedAt = now, dirty = 1))
        }
        db.bodyMetricDao().getAll().forEach {
            db.bodyMetricDao().upsert(it.copy(updatedAt = now, dirty = 1))
        }
        db.userProfileDao().get()?.let {
            db.userProfileDao().upsert(it.copy(updatedAt = now, dirty = 1))
        }
        // Образцы — одним запросом: upsert с заменой строки задел бы
        // их упражнения, а пометить нужно только саму запись.
        db.myWorkoutDao().markAllDirty(now)
        ExerciseNotes.touchAll(appCtx)
    }

    /**
     * Стереть местные данные перед приёмом с сервера.
     *
     * Отметки об удалении тоже убираем: иначе они уехали бы на сервер
     * и удалили там ровно то, что мы собираемся получить.
     */
    /**
     * Стереть дневник с этого телефона.
     *
     * Нужно после выхода из учётной записи: человек вышел и хочет,
     * чтобы после него на общем или чужом телефоне ничего не осталось.
     * Идти для этого в настройки Android и стирать данные приложения —
     * совет для другого человека.
     *
     * Записи на сервере это не трогает: войдёт снова — они вернутся.
     * Отметку последнего обмена сбрасываем, иначе после нового входа
     * сервер прислал бы только изменения с той поры, а всё, что было
     * до неё, не вернулось бы никогда.
     */
    suspend fun wipeDevice() = withContext(Dispatchers.IO) {
        wipeLocal()
        prefs.forgetSyncPoint()
    }

    private suspend fun wipeLocal() {
        db.workoutHistoryDao().getAllWorkouts().forEach {
            db.completedWorkoutExerciseDao().deleteByWorkout(it.id)
            db.completedWorkoutSetDao().deleteByWorkout(it.id)
        }
        db.workoutHistoryDao().clear()
        db.plannedWorkoutDao().getAll().forEach {
            db.plannedWorkoutDao().deleteExercisesByWorkout(it.id)
            db.plannedWorkoutDao().deleteById(it.id)
        }
        db.bodyMetricDao().getAll().forEach { db.bodyMetricDao().deleteById(it.id) }
        db.myWorkoutDao().getAll().forEach {
            db.myWorkoutDao().deleteExercises(it.id)
            db.myWorkoutDao().deleteById(it.id)
        }
        db.customExerciseDao().clear()
        db.exerciseHistoryDao().clear()
        db.syncTombstoneDao().clear()
        ExerciseNotes.clear(appCtx)
    }

    // ═══════════════════ УПАКОВКА ═══════════════════

    /**
     * Образец в том виде, в каком его ждут браузер и мини-приложение:
     * название, группы строкой и простой список названий упражнений.
     *
     * Именно список строк, а не список объектов: так это устроено
     * в веб-версии, а вид обмена у нас общий. Отличаться здесь значило бы
     * заставить обе стороны понимать два вида одного и того же.
     */
    private suspend fun myWorkoutPayload(m: MyWorkoutEntity): JSONObject {
        val names = JSONArray()
        db.myWorkoutDao().getExercises(m.id).forEach { names.put(it.exerciseName) }
        return JSONObject()
            .put("title", m.title)
            .put("muscleGroup", m.muscleGroup)
            .put("exercises", names)
    }

    private fun envelope(kind: String, uuid: String, updatedAt: Long, payload: JSONObject) =
        JSONObject()
            .put("kind", kind)
            .put("uuid", uuid)
            .put("updatedAt", updatedAt)
            .put("deleted", false)
            .put("payload", payload)

    /**
     * Тренировка едет одним куском вместе с упражнениями и подходами.
     * Разбивать её на отдельные синхронизируемые записи смысла нет:
     * подход без своей тренировки бессмыслен, а слияние по кусочкам
     * дало бы половинчатые тренировки при обрыве связи.
     */
    private suspend fun workoutPayload(w: CompletedWorkoutEntity): JSONObject {
        val exercises = JSONArray()
        db.completedWorkoutExerciseDao().getByWorkout(w.id).forEach { e ->
            exercises.put(JSONObject()
                .put("name", e.exerciseName)
                .putOpt("muscleGroup", e.muscleGroup)
                .putOpt("measureType", e.measureType))
        }
        val sets = JSONArray()
        db.completedWorkoutSetDao().getByWorkout(w.id).forEach { s ->
            sets.put(JSONObject()
                .put("exerciseName", s.exerciseName)
                .put("weight", s.weight.toDouble())
                .put("reps", s.reps)
                .putOpt("seconds", s.seconds)
                .putOpt("distanceKm", s.distanceKm?.toDouble())
                .putOpt("load", s.load?.toDouble())
                .putOpt("speed", s.speed?.toDouble()))
        }
        return JSONObject()
            .put("date", w.date)
            .putOpt("duration", w.duration)
            .putOpt("athleteWeight", w.athleteWeight?.toDouble())
            .put("muscleGroup", w.muscleGroup)
            .putOpt("notes", w.notes)
            .put("exercises", exercises)
            .put("sets", sets)
    }

    private suspend fun plannedPayload(p: PlannedWorkoutEntity): JSONObject {
        val exercises = JSONArray()
        // Цели лежат отдельным полем, а не внутри списка упражнений.
        // Так список остаётся тем же, чем был, — простым перечнем имён,
        // и старые устройства с веб-версией читают его как прежде.
        val targets = JSONObject()
        db.plannedWorkoutDao().getExercises(p.id).forEach { e ->
            exercises.put(e.exerciseName)
            val plan = ExerciseTarget.planFromString(e.targetPlan)
            if (e.targetSets != null || e.targetReps != null || e.targetWeight != null ||
                plan.isNotEmpty()) {
                targets.put(
                    e.exerciseName,
                    JSONObject()
                        .putOpt("sets", e.targetSets)
                        .putOpt("reps", e.targetReps)
                        .putOpt("weight", e.targetWeight?.toDouble())
                        // План тренера по подходам — массивом [{w, r}].
                        .putOpt("plan", plan.takeIf { it.isNotEmpty() }
                            ?.let { ExerciseTarget.planToJson(it) })
                )
            }
        }
        return JSONObject()
            .put("date", p.date)
            .put("hour", p.hour)
            .put("minute", p.minute)
            .put("muscleGroup", p.muscleGroup)
            .put("exercises", exercises)
            .put("targets", targets)
    }

    private fun metricPayload(m: BodyMetricEntity) = JSONObject()
        .put("date", m.date)
        .putOpt("weight", m.weight?.toDouble())
        .putOpt("chest", m.chest?.toDouble())
        .putOpt("waist", m.waist?.toDouble())
        .putOpt("hips", m.hips?.toDouble())
        .putOpt("biceps", m.biceps?.toDouble())
        .putOpt("forearm", m.forearm?.toDouble())
        .putOpt("thigh", m.thigh?.toDouble())
        .putOpt("calf", m.calf?.toDouble())
        .putOpt("neck", m.neck?.toDouble())
        .putOpt("shoulders", m.shoulders?.toDouble())

    // ═══════════════════ РАСПАКОВКА ═══════════════════

    private suspend fun applyUpsert(kind: String, uuid: String, updatedAt: Long, p: JSONObject?) {
        if (p == null) return
        when (kind) {
            ExerciseNotes.KIND ->
                ExerciseNotes.applyRemote(appCtx, p.str("name"), p.str("text"), updatedAt)
            SyncKind.WORKOUT -> {
                val old = db.workoutHistoryDao().getByUuid(uuid)
                val id = db.workoutHistoryDao().upsert(
                    CompletedWorkoutEntity(
                        id = old?.id ?: 0,
                        date = p.optLong("date"),
                        duration = p.opt("duration")?.let { (it as? Number)?.toLong() },
                        athleteWeight = p.opt("athleteWeight")?.let { (it as? Number)?.toFloat() },
                        muscleGroup = p.str("muscleGroup"),
                        notes = p.str("notes").takeIf { it.isNotBlank() },
                        uuid = uuid, updatedAt = updatedAt, dirty = 0
                    )
                )
                val workoutId = if (id > 0) id else (old?.id ?: return)
                // Дети переписываются целиком: тренировка приехала
                // как единое целое, склеивать её по частям незачем.
                db.completedWorkoutExerciseDao().deleteByWorkout(workoutId)
                db.completedWorkoutSetDao().deleteByWorkout(workoutId)

                val exercises = p.optJSONArray("exercises") ?: JSONArray()
                for (i in 0 until exercises.length()) {
                    val e = exercises.optJSONObject(i) ?: continue
                    db.completedWorkoutExerciseDao().insert(
                        CompletedWorkoutExerciseEntity(
                            workoutId = workoutId,
                            exerciseName = e.str("name"),
                            muscleGroup = e.str("muscleGroup").takeIf { it.isNotBlank() },
                            measureType = e.str("measureType").takeIf { it.isNotBlank() }
                        )
                    )
                }
                val sets = p.optJSONArray("sets") ?: JSONArray()
                for (i in 0 until sets.length()) {
                    val s = sets.optJSONObject(i) ?: continue
                    db.completedWorkoutSetDao().insert(
                        CompletedWorkoutSetEntity(
                            workoutId = workoutId,
                            exerciseName = s.str("exerciseName"),
                            weight = s.optDouble("weight", 0.0).toFloat(),
                            reps = s.optInt("reps"),
                            seconds = s.opt("seconds")?.let { (it as? Number)?.toInt() },
                            distanceKm = s.opt("distanceKm")?.let { (it as? Number)?.toFloat() },
                            load = s.opt("load")?.let { (it as? Number)?.toFloat() },
                            speed = s.opt("speed")?.let { (it as? Number)?.toFloat() }
                        )
                    )
                }
            }

            SyncKind.MY_WORKOUT -> {
                val old = db.myWorkoutDao().getByUuid(uuid)
                val id = db.myWorkoutDao().upsert(
                    MyWorkoutEntity(
                        id = old?.id ?: 0,
                        title = p.str("title"),
                        muscleGroup = p.str("muscleGroup"),
                        uuid = uuid, updatedAt = updatedAt, dirty = 0
                    )
                )
                val myId = if (id > 0) id else (old?.id ?: return)
                // Набор переписывается целиком: образец приехал как одно
                // целое, и сливать его по упражнениям незачем — порядок
                // в нём значит не меньше состава.
                db.myWorkoutDao().deleteExercises(myId)
                val names = p.optJSONArray("exercises") ?: JSONArray()
                for (i in 0 until names.length()) {
                    val name = names.strAt(i)
                    if (name.isBlank()) continue
                    db.myWorkoutDao().insertExercise(
                        MyWorkoutExerciseEntity(
                            myWorkoutId = myId, exerciseName = name, position = i
                        )
                    )
                }
            }

            SyncKind.PLANNED -> {
                val old = db.plannedWorkoutDao().getByUuid(uuid)
                val id = db.plannedWorkoutDao().upsert(
                    PlannedWorkoutEntity(
                        id = old?.id ?: 0,
                        date = p.optLong("date"),
                        hour = p.optInt("hour"),
                        minute = p.optInt("minute"),
                        muscleGroup = p.str("muscleGroup"),
                        uuid = uuid, updatedAt = updatedAt, dirty = 0
                    )
                )
                val workoutId = if (id > 0) id else (old?.id ?: return)
                db.plannedWorkoutDao().deleteExercisesByWorkout(workoutId)
                val list = p.optJSONArray("exercises") ?: JSONArray()
                val targets = p.optJSONObject("targets")
                for (i in 0 until list.length()) {
                    val name = list.strAt(i)
                    // Поля targets может не быть вовсе: план мог прийти
                    // со старой версии приложения. Тогда просто нет целей.
                    val t = targets?.optJSONObject(name)
                    db.plannedWorkoutDao().insertExercise(
                        PlannedExerciseEntity(
                            workoutId = workoutId, exerciseName = name,
                            targetSets = t?.optInt("sets")?.takeIf { it > 0 },
                            targetReps = t?.optInt("reps")?.takeIf { it > 0 },
                            targetWeight = t?.optDouble("weight")?.toFloat()
                                ?.takeIf { it > 0f && !it.isNaN() },
                            targetPlan = ExerciseTarget.planFromJson(t?.optJSONArray("plan"))
                                .takeIf { it.isNotEmpty() }
                                ?.let { ExerciseTarget.planToJson(it).toString() }
                        )
                    )
                }
            }

            SyncKind.CUSTOM_EXERCISE -> {
                val old = db.customExerciseDao().getByUuid(uuid)
                db.customExerciseDao().upsert(
                    CustomExerciseEntity(
                        id = old?.id ?: 0,
                        name = p.str("name"),
                        muscleGroup = p.str("muscleGroup"),
                        measureType = p.str("measureType").takeIf { it.isNotBlank() },
                        description = p.str("description").takeIf { it.isNotBlank() },
                        // Три случая, и путать их нельзя — те же, что
                        // у фото профиля:
                        //   поля нет вовсе  — про снимок ничего не сказали
                        //                     (старая версия), оставляем своё;
                        //   поле есть, null — снимок УБРАЛИ на той стороне;
                        //   поле есть, текст — приняли новый.
                        //
                        // Раньше пустое поле означало «оставь своё», и убрать
                        // снимок из браузера было нельзя: он возвращался при
                        // следующем обмене, будто его и не трогали.
                        photoPath = when {
                            !p.has("photo") -> old?.photoPath
                            p.isNull("photo") -> null
                            else -> ExercisePhoto.decode(filesDir, p.str("photo"))
                                ?: old?.photoPath   // не разобрали — лучше своё, чем ничего
                        },
                        uuid = uuid, updatedAt = updatedAt, dirty = 0
                    )
                )
            }

            SyncKind.BODY_METRIC -> {
                val old = db.bodyMetricDao().getByUuid(uuid)
                db.bodyMetricDao().upsert(
                    BodyMetricEntity(
                        id = old?.id ?: 0,
                        date = p.optLong("date"),
                        weight = p.num("weight"), chest = p.num("chest"), waist = p.num("waist"),
                        hips = p.num("hips"), biceps = p.num("biceps"), forearm = p.num("forearm"),
                        thigh = p.num("thigh"), calf = p.num("calf"), neck = p.num("neck"),
                        shoulders = p.num("shoulders"),
                        uuid = uuid, updatedAt = updatedAt, dirty = 0
                    )
                )
            }

            SyncKind.PROFILE -> {
                val old = db.userProfileDao().get()
                // Три разных случая, и путать их нельзя:
                //  поле есть и не пустое — пришло фото, раскладываем в файл;
                //  поле есть и пустое    — фото убрали на той стороне;
                //  поля нет вовсе        — про фото ничего не сказали,
                //                          оставляем своё (так шлёт телефон
                //                          без снимка и старые версии).
                val photo = when {
                    !p.has("photo") -> old?.photoPath
                    p.isNull("photo") -> null
                    else -> ProfilePhoto.decode(filesDir, p.str("photo"))
                        ?: old?.photoPath   // не разобрали — лучше своё, чем ничего
                }
                if (photo != old?.photoPath) {
                    ProfilePhoto.cleanUp(filesDir, keep = photo?.substringAfterLast('/'))
                }
                /* Пол — те же три случая, что у фото:
                     поля нет вовсе — про пол ничего не сказали, своё
                       оставляем (так шлёт телефон, где его не выбирали);
                     поле есть и пустое — выбор сняли на той стороне;
                     поле есть со значением — берём его. */
                val sex = when {
                    !p.has("sex") -> old?.sex
                    p.isNull("sex") -> null
                    else -> p.str("sex").takeIf { it == "m" || it == "f" }
                }
                /* Имя, рост и вес — по тому же правилу «нет поля — оставь своё».

                   Раньше они заменялись всегда: не пришло имя — стало
                   пустым. Так и выходило «Ваше имя» вместо настоящего,
                   когда с сервера приезжал урезанный профиль. Сервер
                   теперь склеивает профиль по полям и урезанных
                   не отдаёт, но и телефон не должен стирать то, о чём
                   ему ничего не сказали. */
                val name = when {
                    !p.has("name") -> old?.name
                    p.isNull("name") -> null
                    else -> p.str("name").takeIf { it.isNotBlank() }
                }
                val heightCm = if (!p.has("heightCm")) old?.heightCm else p.num("heightCm")
                val weightKg = if (!p.has("weightKg")) old?.weightKg else p.num("weightKg")
                db.userProfileDao().upsert(
                    UserProfileEntity(
                        id = 1,
                        name = name,
                        photoPath = photo,
                        heightCm = heightCm,
                        weightKg = weightKg,
                        sex = sex,
                        uuid = "me", updatedAt = updatedAt, dirty = 0
                    )
                )
            }
        }
    }

    private suspend fun applyDelete(kind: String, uuid: String) {
        when (kind) {
            SyncKind.WORKOUT -> db.workoutHistoryDao().getByUuid(uuid)?.let {
                db.completedWorkoutExerciseDao().deleteByWorkout(it.id)
                db.completedWorkoutSetDao().deleteByWorkout(it.id)
                db.workoutHistoryDao().deleteById(it.id)
            }
            SyncKind.PLANNED -> db.plannedWorkoutDao().getByUuid(uuid)?.let {
                db.plannedWorkoutDao().deleteExercisesByWorkout(it.id)
                db.plannedWorkoutDao().deleteById(it.id)
            }
            SyncKind.MY_WORKOUT -> db.myWorkoutDao().getByUuid(uuid)?.let {
                db.myWorkoutDao().deleteExercises(it.id)
                db.myWorkoutDao().deleteById(it.id)
            }
            SyncKind.BODY_METRIC -> db.bodyMetricDao().getByUuid(uuid)?.let {
                db.bodyMetricDao().deleteById(it.id)
            }
            // Своё упражнение: убираем только из списка. История в архиве
            // и рекордах привязана к названию и остаётся — удаление
            // упражнения прошлое не переписывает, поэтому его можно
            // принимать и по сети. Профиль по сети не удаляется никогда.
            SyncKind.CUSTOM_EXERCISE -> db.customExerciseDao().getByUuid(uuid)?.let {
                db.customExerciseDao().deleteById(it.id)
            }
        }
    }

    private fun JSONObject.num(key: String): Float? =
        if (isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }?.toFloat()

    // ═══════════════════ РЕКОРДЫ ═══════════════════

    /**
     * Пересчитать рекорды заново по всему архиву.
     *
     * Вызывается после приёма чужих тренировок: старая запись, приехавшая
     * с другого устройства, может оказаться и рекордом, и наоборот —
     * удалённая тренировка могла рекорд забрать с собой.
     */
    private suspend fun rebuildRecords() {
        val workouts = db.workoutHistoryDao().getAllWorkouts().sortedBy { it.date }
        val best = HashMap<String, ExerciseHistoryEntity>()

        workouts.forEach { w ->
            val types = db.completedWorkoutExerciseDao().getByWorkout(w.id)
                .associate { it.exerciseName to (it.measureType ?: "WEIGHT_REPS") }

            db.completedWorkoutSetDao().getByWorkout(w.id).forEach { s ->
                val name = s.exerciseName
                val type = types[name] ?: "WEIGHT_REPS"
                val cur = best[name] ?: ExerciseHistoryEntity(
                    exerciseName = name, maxWeight = 0f, maxWeightReps = 0,
                    maxReps = 0, bestVolume = 0f, measureType = type
                )

                best[name] = when (type) {
                    "TIME" -> if ((s.seconds ?: 0) > (cur.bestSeconds ?: 0))
                        cur.copy(bestSeconds = s.seconds, recordDate = w.date, measureType = type)
                    else cur.copy(measureType = type)

                    "DISTANCE" -> if ((s.distanceKm ?: 0f) > (cur.bestDistanceKm ?: 0f))
                        cur.copy(
                            bestDistanceKm = s.distanceKm, bestSeconds = s.seconds,
                            recordDate = w.date, measureType = type
                        )
                    else cur.copy(measureType = type)

                    else -> {
                        var next = cur.copy(measureType = type)
                        // Рекорд веса: больший вес, либо тот же вес,
                        // но повторов не меньше прежнего
                        if (s.weight > next.maxWeight ||
                            (s.weight == next.maxWeight && s.reps > next.maxWeightReps)
                        ) {
                            next = next.copy(
                                maxWeight = s.weight, maxWeightReps = s.reps,
                                recordDate = w.date, athleteWeight = w.athleteWeight
                            )
                        }
                        if (s.reps > next.maxReps) next = next.copy(maxReps = s.reps)
                        val volume = PairedExercises.volume(s.weight, s.reps, name).toFloat()
                        if (volume > next.bestVolume) {
                            next = next.copy(
                                bestVolume = volume,
                                bestVolumeWeight = s.weight, bestVolumeReps = s.reps
                            )
                        }
                        next
                    }
                }
            }
        }

        best.values.forEach { db.exerciseHistoryDao().save(it) }
    }
}

// ═══════════════════ ЧТЕНИЕ ТЕКСТА ИЗ ПОСЫЛКИ ═══════════════════

/**
 * Текстовое поле из посылки — или пусто, если его нет.
 *
 * Своя замена optString, и вот почему. В Android optString на пустом
 * значении возвращает не пустую строку, а ТЕКСТ «null» — четыре буквы,
 * которые дальше сохраняются как настоящее значение.
 *
 * Веб-версия отправляет пустые поля явно (иначе устроен JavaScript),
 * поэтому тренировка, записанная в браузере без заметки, приезжала
 * на телефон с заметкой «null». То же с группой мышц у упражнения.
 *
 * Ошибка тихая: приложение не падает, ничего не теряется, просто
 * в дневнике появляется слово из ниоткуда — и понять, откуда оно,
 * человек не может в принципе.
 */
private fun JSONObject.str(name: String): String =
    if (isNull(name)) "" else optString(name)

/** То же для списка: пустое значение внутри списка — пустая строка. */
private fun JSONArray.strAt(index: Int): String =
    if (isNull(index)) "" else optString(index)
