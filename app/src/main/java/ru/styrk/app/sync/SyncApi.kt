package ru.styrk.app.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import ru.styrk.app.data.model.ExerciseTarget
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Разговор с сервером синхронизации.
 *
 * Без сторонних библиотек: HttpURLConnection есть в самом Android, JSON —
 * тоже. Ради пяти запросов тянуть в приложение сетевую библиотеку смысла
 * нет, а каждая такая библиотека потом просит обновлений и приносит свои
 * правила для R8.
 *
 * Договор с сервером описан в SERVER/API.md — если что-то меняется там,
 * меняется и здесь.
 */
class SyncApi(private val prefs: SyncPrefs) {

    /** Ответ сервера: либо данные, либо человеческая причина отказа. */
    sealed class Result<out T> {
        data class Ok<T>(val data: T) : Result<T>()
        /**
         * Отказ.
         *
         * Кроме человеческого текста несёт код и слово ошибки от сервера:
         * один и тот же 404 значит «неверный адрес сервера» при входе
         * и «такого ника нет» у друзей, и решать это должен тот, кто
         * знает, о чём спрашивал.
         */
        data class Fail(
            val message: String,
            val unauthorized: Boolean = false,
            val code: Int = 0,
            val error: String = ""
        ) : Result<Nothing>()
    }

    data class Session(val token: String, val email: String, val serverTime: Long)
    data class Account(
        val email: String,
        val createdAt: Long,
        val items: Int,
        /** Ник для друзей. Сервер заводит его сам при первом обращении. */
        val handle: String? = null,
        /** Не null — человек объявил себя тренером. */
        val coach: CoachCard? = null
    )

    suspend fun register(email: String, password: String): Result<Session> =
        auth("/api/register", email, password)

    suspend fun login(email: String, password: String): Result<Session> =
        auth("/api/login", email, password)

    private suspend fun auth(path: String, email: String, password: String): Result<Session> {
        val body = JSONObject()
            .put("email", email)
            .put("password", password)
            .put("device", android.os.Build.MODEL ?: "android")
        return when (val r = request(path, "POST", body, withToken = false)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                Session(
                    token = r.data.optString("token"),
                    email = r.data.optString("email", email),
                    serverTime = r.data.optLong("serverTime")
                )
            )
        }
    }

    // ── пароль и почта ──

    /** Письмо со ссылкой и кодом. Сервер отвечает одинаково, есть такой адрес или нет. */
    suspend fun forgotPassword(email: String): Result<Unit> =
        when (val r = request("/api/auth/forgot", "POST",
            JSONObject().put("email", email), withToken = false)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    /** Новый пароль по коду из письма. Все прежние входы закрываются, этот — новый. */
    suspend fun resetPassword(email: String, code: String, password: String): Result<Session> {
        val body = JSONObject().put("email", email).put("code", code)
            .put("password", password).put("device", android.os.Build.MODEL ?: "android")
        return when (val r = request("/api/auth/reset", "POST", body, withToken = false)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Session(
                token = r.data.optString("token"),
                email = r.data.optString("email", email),
                serverTime = r.data.optLong("serverTime")))
        }
    }

    // ── тренировка вживую ──

    /** Подход, который вписал тренер. [type] — имя MeasureType. */
    data class LiveOp(
        val id: Long,
        val coach: String,
        val exercise: String,
        val type: String,
        val set: ru.styrk.app.data.model.WorkoutSet
    )

    data class LivePush(val stored: Boolean, val ops: List<LiveOp>)

    /**
     * Снимок идущей тренировки — тренеру. null — тренировка закончилась.
     * В ответ — подходы, которые тренер успел вписать.
     */
    suspend fun livePush(workout: JSONObject?): Result<LivePush> =
        when (val r = request("/api/live", "POST",
            JSONObject().put("workout", workout ?: JSONObject.NULL))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(LivePush(
                stored = r.data.optBoolean("stored"),
                ops = r.data.optJSONArray("ops").mapObjects { o ->
                    val s = o.optJSONObject("set") ?: JSONObject()
                    LiveOp(
                        id = o.optLong("id"),
                        coach = o.optString("coach"),
                        exercise = o.optString("exercise"),
                        type = o.optString("type", "WEIGHT_REPS"),
                        set = ru.styrk.app.data.model.WorkoutSet(
                            weight = s.optDouble("weight", 0.0).toFloat(),
                            reps = s.optInt("reps", 0),
                            seconds = if (s.has("seconds")) s.optInt("seconds") else null,
                            distanceKm = if (s.has("distanceKm")) s.optDouble("distanceKm").toFloat() else null,
                            load = if (s.has("load")) s.optDouble("load").toFloat() else null,
                            speed = if (s.has("speed")) s.optDouble("speed").toFloat() else null
                        )
                    )
                }.filter { it.id > 0 && it.exercise.isNotBlank() }
            ))
        }

    /** Вписал подходы тренера — отмечаю сделанными. */
    suspend fun liveOpsDone(ids: List<Long>): Result<Unit> {
        val a = JSONArray(); ids.forEach { a.put(it) }
        return when (val r = request("/api/live/ops/done", "POST", JSONObject().put("ids", a))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }
    }

    /** Новый пароль по ссылке из письма (App Links). */
    suspend fun resetPasswordByToken(token: String, password: String): Result<Session> {
        val body = JSONObject().put("token", token)
            .put("password", password).put("device", android.os.Build.MODEL ?: "android")
        return when (val r = request("/api/auth/reset", "POST", body, withToken = false)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Session(
                token = r.data.optString("token"),
                email = r.data.optString("email"),
                serverTime = r.data.optLong("serverTime")))
        }
    }

    /** Сменить почту, шаг 1: код уходит на НОВЫЙ адрес. */
    suspend fun emailChangeStart(email: String, password: String): Result<Unit> =
        when (val r = request("/api/account/email", "POST",
            JSONObject().put("email", email).put("password", password))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    /** Шаг 2: код из письма. Ответ — новый адрес. */
    suspend fun emailChangeConfirm(code: String): Result<String> =
        when (val r = request("/api/account/email/confirm", "POST", JSONObject().put("code", code))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(r.data.optString("email"))
        }

    suspend fun me(): Result<Account> =
        when (val r = request("/api/me", "GET", null)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                Account(
                    email = r.data.optString("email"),
                    createdAt = r.data.optLong("createdAt"),
                    items = r.data.optInt("items"),
                    handle = r.data.optString("handle").takeIf { it.isNotBlank() },
                    coach = coachOf(r.data.optJSONObject("coach"))
                )
            )
        }

    suspend fun logout(): Result<Unit> =
        when (val r = request("/api/logout", "POST", JSONObject())) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    suspend fun deleteAccount(password: String): Result<Unit> =
        when (val r = request("/api/account", "DELETE", JSONObject().put("password", password))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    /** Проверка связи — без аккаунта, чтобы отличить «сервер не отвечает» от «неверный пароль». */
    suspend fun health(): Result<Unit> =
        when (val r = request("/api/health", "GET", null, withToken = false)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    /** Обмен изменениями. Тело и разбор ответа — на стороне SyncManager. */
    suspend fun sync(body: JSONObject): Result<JSONObject> =
        request("/api/sync", "POST", body)

    // ═════════════════ ДРУЗЬЯ ═════════════════
    //
    // Сервер здесь отдаёт не данные друга, а ответ на вопрос: он сам
    // считает рекорды и объём и возвращает только то, что владелец
    // открыл именно нам. Поэтому разбирать тут почти нечего — числа
    // приходят готовыми.

    /** Что человек открыл кому-то одному. */
    data class Share(
        val records: Boolean = false,
        val volume: Boolean = false,
        /**
         * «Он мой тренер»: видит дневник целиком и планирует за меня.
         *
         * Нарочно не в одном ряду с показом. Рекорды и объём — про
         * показать; это — про доверить. Мешать их в один список значило
         * бы позволить отдать доверие, целясь в показ.
         */
        val coach: Boolean = false,
        /** Замеры тела, вес и снимок. Только вместе с тренерством. */
        val body: Boolean = false,
        /**
         * График веса на весах — и только он.
         *
         * Не «рекорды»: те говорят, сколько человек жмёт, а вес говорит,
         * сколько он весит, и это скрывают чаще. И не «тело»: замеры
         * со снимком уходят только тренеру, а весом мериться
         * с приятелем — обычное дело.
         */
        val weight: Boolean = false,
        /** До какого дня открыт доступ тренеру. null — бессрочно. */
        val until: Long? = null
    ) {
        val nothing: Boolean get() = !records && !volume
    }

    /** Карточка тренера: то единственное, что о нём знает ученик. */
    data class CoachCard(val name: String, val gym: String, val since: Long)

    data class Friend(
        val handle: String,
        val since: Long,
        /** Что я открыл ему. */
        val share: Share,
        /** Что он открыл мне. */
        val theirShare: Share,
        /** Не null — этот друг объявил себя тренером. */
        val coach: CoachCard? = null,
        /** Моё пожелание «когда удобно» этому тренеру; null — нет. */
        val myWish: String? = null
    )

    /** Заявка — входящая или исходящая, отличает их список, в котором лежит. */
    data class Knock(val handle: String, val at: Long, val coach: CoachCard? = null)

    data class Friends(
        val handle: String,
        val friends: List<Friend>,
        val incoming: List<Knock>,
        val outgoing: List<Knock>
    )

    /**
     * Рекорд друга. Поля те же, что у своего рекорда, и заполнены
     * не все: у планки нет веса, у бега нет повторов.
     */
    data class FriendRecord(
        val name: String,
        val measureType: String,
        val maxWeight: Float = 0f,
        val maxWeightReps: Int = 0,
        val maxReps: Int = 0,
        val bestVolume: Float = 0f,
        val bestSeconds: Int? = null,
        val bestDistanceKm: Float? = null,
        val recordDate: Long? = null,
        /**
         * Вес человека на день рекорда.
         *
         * Сервер присылал его и раньше, а телефон выбрасывал — некому
         * было показывать. Теперь есть: на доске друзей сумма делится
         * на вес тела, иначе сравниваются не люди, а весовые категории.
         */
        val athleteWeight: Float? = null
    )

    data class Volume(val kg: Long, val sets: Int, val workouts: Int)

    data class Comparison(
        val handle: String,
        val allowed: Share,
        /** null — категория закрыта. Пустой список — открыта, но пусто. */
        val records: List<FriendRecord>?,
        val volume: Volume?
    )

    /** Стать тренером или поправить свою карточку. */
    suspend fun becomeCoach(name: String, gym: String): Result<CoachCard?> =
        when (val r = request("/api/coach", "POST", JSONObject()
            .put("name", name).put("gym", gym))
        ) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(coachOf(r.data.optJSONObject("coach")))
        }

    /**
     * Перестать быть тренером.
     *
     * Согласия учеников при этом на сервере НЕ стираются: человек мог
     * перепутать кнопку, а собирать их заново пришлось бы у живых людей.
     * Права всё равно не действуют — сервер каждый раз спрашивает,
     * тренер ли он сейчас.
     */
    suspend fun quitCoach(): Result<Unit> =
        when (val r = request("/api/coach", "DELETE", JSONObject())) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    data class Student(val handle: String, val body: Boolean, val since: Long)

    suspend fun students(): Result<List<Student>> =
        when (val r = request("/api/coach/students", "GET", null)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                r.data.optJSONArray("students").mapObjects {
                    Student(
                        handle = it.optString("handle"),
                        body = it.optBoolean("body"),
                        since = it.optLong("since")
                    )
                }
            )
        }

    /**
     * «Когда мне удобно» — строка своими словами.
     *
     * Не форма с днями недели и часами: разбирать её на поля значило
     * бы заставить человека заполнять таблицу там, где хватает фразы
     * «по будням после семи». Тренер всё равно прочтёт её глазами.
     */
    suspend fun wish(handle: String, text: String): Result<Unit> =
        when (val r = request("/api/coach/wish", "POST", JSONObject()
            .put("handle", handle).put("text", text))
        ) {
            is Result.Fail -> Result.Fail(
                message = if (r.error == "not_your_coach")
                    "Это не ваш тренер" else r.message,
                unauthorized = r.unauthorized, code = r.code, error = r.error
            )
            is Result.Ok -> Result.Ok(Unit)
        }

    /** Абонемент, заведённый тренером. Правит его тренер, видит — ученик. */
    data class Sub(
        val from: String, val gym: String, val handle: String,
        val total: Int?, val left: Int?, val until: Long?, val note: String
    )

    /** Заметка тренера к выполненной тренировке. */
    data class CoachComment(val uuid: String, val text: String, val from: String, val at: Long)

    data class MyCoachStuff(val subs: List<Sub>, val comments: List<CoachComment>)

    /**
     * Своё тренерское: абонементы и заметки.
     *
     * Одним запросом нарочно: и то, и другое человек смотрит в одном
     * месте и в одну минуту, а два запроса означали бы два состояния
     * загрузки и две причины для пустого экрана.
     */
    suspend fun myCoachStuff(): Result<MyCoachStuff> =
        when (val r = request("/api/my-coach", "GET", null)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                MyCoachStuff(
                    subs = r.data.optJSONArray("subs").mapObjects {
                        Sub(
                            from = it.optString("from"),
                            gym = it.optString("gym"),
                            handle = it.optString("handle"),
                            total = if (it.isNull("total")) null else it.optInt("total"),
                            left = if (it.isNull("left")) null else it.optInt("left"),
                            until = if (it.isNull("until")) null else it.optLong("until"),
                            note = if (it.isNull("note")) "" else it.optString("note")
                        )
                    },
                    comments = r.data.optJSONArray("comments").mapObjects {
                        CoachComment(
                            uuid = it.optString("uuid"),
                            text = it.optString("text"),
                            from = it.optString("from"),
                            at = it.optLong("at")
                        )
                    }
                )
            )
        }

    suspend fun setHandle(handle: String): Result<String> =
        when (val r = request("/api/handle", "POST", JSONObject().put("handle", handle))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(r.data.optString("handle"))
        }

    suspend fun friends(): Result<Friends> =
        when (val r = request("/api/friends", "GET", null)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                Friends(
                    handle = r.data.optString("handle"),
                    friends = r.data.optJSONArray("friends").mapObjects { o ->
                        Friend(
                            handle = o.optString("handle"),
                            since = o.optLong("since"),
                            share = shareOf(o.optJSONObject("share")),
                            theirShare = shareOf(o.optJSONObject("theirShare")),
                            coach = coachOf(o.optJSONObject("coach")),
                            myWish = o.optJSONObject("myWish")?.optString("text")
                                ?.takeIf { it.isNotBlank() }
                        )
                    },
                    incoming = r.data.optJSONArray("incoming").mapObjects { knockOf(it) },
                    outgoing = r.data.optJSONArray("outgoing").mapObjects { knockOf(it) }
                )
            )
        }

    /** Позвать в друзья. Возвращает состояние: pending или сразу accepted. */
    suspend fun friendRequest(handle: String): Result<String> =
        when (val r = request("/api/friends/request", "POST", JSONObject().put("handle", handle))) {
            is Result.Fail -> Result.Fail(
                message = if (r.code == 404) "Такого ника нет" else r.message,
                unauthorized = r.unauthorized, code = r.code, error = r.error
            )
            is Result.Ok -> Result.Ok(r.data.optString("status"))
        }

    suspend fun friendAccept(handle: String): Result<Unit> = answer("accept", handle)
    suspend fun friendDecline(handle: String): Result<Unit> = answer("decline", handle)

    private suspend fun answer(what: String, handle: String): Result<Unit> =
        when (val r = request("/api/friends/$what", "POST", JSONObject().put("handle", handle))) {
            is Result.Fail -> Result.Fail(
                // Заявку могли отозвать с той стороны, пока экран был открыт.
                message = if (r.code == 404) "Заявки больше нет" else r.message,
                unauthorized = r.unauthorized, code = r.code, error = r.error
            )
            is Result.Ok -> Result.Ok(Unit)
        }

    suspend fun friendRemove(handle: String): Result<Unit> =
        when (val r = request("/api/friends/remove", "POST", JSONObject().put("handle", handle))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    /**
     * Что я открываю этому другу.
     *
     * Оба поля посылаются всегда, а не только изменённое: половинчатая
     * посылка означала бы, что второе поле решает сервер, а решает его
     * человек — здесь, галочкой.
     */
    suspend fun friendShare(handle: String, share: Share): Result<Share> =
        when (val r = request("/api/friends/share", "POST", JSONObject()
            .put("handle", handle)
            .put("records", share.records)
            .put("volume", share.volume)
            .put("coach", share.coach)
            .put("body", share.body)
            .put("weight", share.weight)
            .put("until", share.until ?: JSONObject.NULL))
        ) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(shareOf(r.data.optJSONObject("share")))
        }

    /** Точка линии: номер дня и значение. */
    data class Dot(val d: Long, val v: Float)

    /**
     * Ряды для графика. Считает их сервер — и свой тоже.
     *
     * Считать свою линию на телефоне было бы естественнее и хуже: две
     * линии на одном поле обязаны быть посчитаны одинаково, а два
     * разных счёта разойдутся на первом же спорном подходе.
     *
     * `theirs` = null — не открыто. Именно null, а не пустой список:
     * пустой значил бы «делал, но ничего не выжал».
     */
    data class Series(
        val kind: String,
        val unit: String,
        val allowed: Boolean,
        val mine: List<Dot>,
        val theirs: List<Dot>?,
        /** Один считает повторы, другой килограммы — линии сравнивать нельзя. */
        val mixed: Boolean = false
    )

    suspend fun series(handle: String, exercise: String? = null): Result<Series> =
        when (val r = request(
            "/api/friends/series?handle=" + java.net.URLEncoder.encode(handle, "UTF-8") +
                (exercise?.takeIf { it.isNotBlank() }
                    ?.let { "&ex=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: ""),
            "GET", null
        )) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                Series(
                    kind = r.data.optString("kind", "weight"),
                    unit = r.data.optString("unit", ""),
                    allowed = r.data.optBoolean("allowed"),
                    mine = dotsOf(r.data.optJSONArray("mine")),
                    theirs = if (r.data.has("theirs")) dotsOf(r.data.optJSONArray("theirs")) else null,
                    mixed = r.data.optBoolean("mixed")
                )
            )
        }

    suspend fun compare(handle: String): Result<Comparison> =
        when (val r = request(
            // Ник проверен на той стороне, но в адрес его всё равно
            // кладём с оглядкой: в поле ввода человек может набрать что
            // угодно, а незакодированный пробел ломает не проверку,
            // а сам запрос.
            "/api/friends/compare?handle=" + java.net.URLEncoder.encode(handle, "UTF-8"),
            "GET", null
        )) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                Comparison(
                    handle = r.data.optString("handle", handle),
                    allowed = shareOf(r.data.optJSONObject("allowed")),
                    // Поля нет — категория закрыта. Именно нет, а не пусто:
                    // пустой список значил бы «он ничего не поднимал».
                    records = if (r.data.has("records")) recordsOf(r.data.optJSONArray("records")) else null,
                    volume = r.data.optJSONObject("volume")?.let { volumeOf(it) }
                )
            )
        }

    /**
     * Свои числа теми же правилами, что и чужие.
     *
     * Считать свою колонку на телефоне нельзя: она вышла бы из своей
     * базы, а чужая — из присланного на сервер. Разойдутся они ровно
     * в тот день, когда человек не успел синхронизироваться.
     */
    suspend fun myStats(): Result<Comparison> =
        when (val r = request("/api/stats", "GET", null)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                Comparison(
                    handle = "",
                    allowed = Share(records = true, volume = true),
                    records = recordsOf(r.data.optJSONArray("records")),
                    volume = r.data.optJSONObject("volume")?.let { volumeOf(it) }
                )
            )
        }

    // ═════════════════ ЗОВ НА СОВМЕСТНУЮ ТРЕНИРОВКУ ═════════════════
    //
    // Зов везёт КОПИЮ плана, а не ссылку на него. Общий план на двоих
    // был бы удобнее на словах и хуже на деле: двое правят одну запись
    // с разных устройств, и кто-то однажды обнаружит, что его правку
    // стёрли. Копия ведёт себя понятно: принял — план лёг в календарь
    // и дальше его.

    data class InvitePlan(
        /** Номер дня, как в базе планов, — не миллисекунды. */
        val date: Long,
        val hour: Int,
        val minute: Int,
        val muscleGroup: String,
        val exercises: List<String>,
        val targets: Map<String, ExerciseTarget>
    )

    data class Invite(
        val id: Long,
        val handle: String,
        val at: Long,
        /** pending | accepted | declined */
        val status: String,
        /** Причина отказа — от позванного и после. */
        val note: String,
        /** friend — зов приятеля, coach — назначение тренера. */
        val kind: String = "friend",
        /** Заметка тренера к назначению — от тренера и до тренировки. */
        val planNote: String = "",
        val plan: InvitePlan
    ) {
        val fromCoach: Boolean get() = kind == "coach"
    }

    data class Invites(
        val incoming: List<Invite>,
        val outgoing: List<Invite>,
        /** Всё, что назначил тренер, — с ответами, за два месяца и вперёд. */
        val coachPlans: List<Invite> = emptyList()
    )

    suspend fun invites(): Result<Invites> =
        when (val r = request("/api/invites", "GET", null)) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(
                Invites(
                    incoming = r.data.optJSONArray("incoming").mapObjects { inviteOf(it) },
                    outgoing = r.data.optJSONArray("outgoing").mapObjects { inviteOf(it) },
                    coachPlans = r.data.optJSONArray("coachPlans").mapObjects { inviteOf(it) }
                )
            )
        }

    suspend fun invite(handle: String, plan: InvitePlan): Result<Unit> =
        when (val r = request("/api/invites", "POST", JSONObject()
            .put("handle", handle)
            .put("plan", planJson(plan)))
        ) {
            is Result.Fail -> Result.Fail(
                message = if (r.code == 404) "Такого ника нет" else r.message,
                unauthorized = r.unauthorized, code = r.code, error = r.error
            )
            is Result.Ok -> Result.Ok(Unit)
        }

    /** Ответ на зов. При согласии сервер возвращает план — его и кладём себе. */
    suspend fun inviteAnswer(id: Long, yes: Boolean, note: String = ""): Result<InvitePlan?> =
        when (val r = request("/api/invites/answer", "POST", JSONObject()
            .put("id", id).put("yes", yes).put("note", note))
        ) {
            is Result.Fail -> Result.Fail(
                message = if (r.code == 404) "Зова больше нет" else r.message,
                unauthorized = r.unauthorized, code = r.code, error = r.error
            )
            is Result.Ok -> Result.Ok(
                r.data.optJSONObject("plan")?.let { planOf(it) }
            )
        }

    suspend fun inviteCancel(id: Long): Result<Unit> =
        when (val r = request("/api/invites/cancel", "POST", JSONObject().put("id", id))) {
            is Result.Fail -> r
            is Result.Ok -> Result.Ok(Unit)
        }

    private fun planJson(p: InvitePlan): JSONObject {
        val ex = JSONArray()
        p.exercises.forEach { ex.put(it) }
        val tg = JSONObject()
        p.targets.forEach { (name, t) ->
            if (t.isEmpty) return@forEach
            tg.put(name, JSONObject()
                .putOpt("sets", t.sets)
                .putOpt("reps", t.reps)
                .putOpt("weight", t.weight?.toDouble())
                .putOpt("plan", t.plan.takeIf { it.isNotEmpty() }
                    ?.let { ExerciseTarget.planToJson(it) }))
        }
        return JSONObject()
            .put("date", p.date)
            .put("hour", p.hour)
            .put("minute", p.minute)
            .put("muscleGroup", p.muscleGroup)
            .put("exercises", ex)
            .put("targets", tg)
    }

    private fun inviteOf(o: JSONObject): Invite = Invite(
        id = o.optLong("id"),
        handle = o.optString("handle"),
        at = o.optLong("at"),
        status = o.optString("status").ifBlank { "pending" },
        note = if (o.isNull("note")) "" else o.optString("note"),
        kind = o.optString("kind").ifBlank { "friend" },
        planNote = if (o.isNull("planNote")) "" else o.optString("planNote"),
        plan = o.optJSONObject("plan")?.let { planOf(it) }
            ?: InvitePlan(0, 19, 0, "", emptyList(), emptyMap())
    )

    private fun planOf(o: JSONObject): InvitePlan {
        val names = ArrayList<String>()
        o.optJSONArray("exercises")?.let { a ->
            for (i in 0 until a.length()) a.optString(i).takeIf { it.isNotBlank() }?.let(names::add)
        }
        val targets = HashMap<String, ExerciseTarget>()
        o.optJSONObject("targets")?.let { t ->
            val keys = t.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = t.optJSONObject(k) ?: continue
                targets[k] = ExerciseTarget(
                    sets = if (v.isNull("sets")) null else v.optInt("sets").takeIf { it > 0 },
                    reps = if (v.isNull("reps")) null else v.optInt("reps").takeIf { it > 0 },
                    weight = if (v.isNull("weight")) null
                             else v.optDouble("weight").toFloat().takeIf { it > 0f },
                    plan = ExerciseTarget.planFromJson(v.optJSONArray("plan"))
                )
            }
        }
        return InvitePlan(
            date = o.optLong("date"),
            hour = o.optInt("hour", 19),
            minute = o.optInt("minute"),
            muscleGroup = o.optString("muscleGroup"),
            exercises = names,
            targets = targets
        )
    }

    // ── разбор ──

    private fun shareOf(o: JSONObject?): Share = Share(
        records = o?.optBoolean("records") == true,
        volume = o?.optBoolean("volume") == true,
        coach = o?.optBoolean("coach") == true,
        body = o?.optBoolean("body") == true,
        weight = o?.optBoolean("weight") == true,
        until = if (o == null || o.isNull("until")) null else o.optLong("until").takeIf { it > 0 }
    )

    private fun dotsOf(a: org.json.JSONArray?): List<Dot> {
        val out = ArrayList<Dot>()
        for (i in 0 until (a?.length() ?: 0)) {
            val o = a!!.optJSONObject(i) ?: continue
            out.add(Dot(d = o.optLong("d"), v = o.optDouble("v", 0.0).toFloat()))
        }
        return out
    }

    private fun coachOf(o: JSONObject?): CoachCard? {
        val name = o?.optString("name").orEmpty()
        if (name.isBlank()) return null
        return CoachCard(name = name, gym = o!!.optString("gym"), since = o.optLong("since"))
    }

    private fun knockOf(o: JSONObject): Knock =
        Knock(handle = o.optString("handle"), at = o.optLong("at"),
            coach = coachOf(o.optJSONObject("coach")))

    private fun volumeOf(o: JSONObject): Volume =
        Volume(kg = o.optLong("kg"), sets = o.optInt("sets"), workouts = o.optInt("workouts"))

    private fun recordsOf(arr: JSONArray?): List<FriendRecord> = arr.mapObjects { o ->
        FriendRecord(
            name = o.optString("name"),
            measureType = o.optString("measureType").ifBlank { "WEIGHT_REPS" },
            maxWeight = o.optDouble("maxWeight", 0.0).toFloat(),
            maxWeightReps = o.optInt("maxWeightReps"),
            maxReps = o.optInt("maxReps"),
            bestVolume = o.optDouble("bestVolume", 0.0).toFloat(),
            // Явный null в JSON у Android читается как строка "null",
            // поэтому пустоту спрашиваем прямо, а не через optString.
            bestSeconds = if (o.isNull("bestSeconds")) null else o.optInt("bestSeconds"),
            bestDistanceKm = if (o.isNull("bestDistanceKm")) null else o.optDouble("bestDistanceKm").toFloat(),
            recordDate = if (o.isNull("recordDate")) null else o.optLong("recordDate").takeIf { it > 0 },
            athleteWeight = if (o.isNull("athleteWeight")) null
                            else o.optDouble("athleteWeight", 0.0).toFloat().takeIf { it > 0f }
        )
    }

    private fun <T> JSONArray?.mapObjects(f: (JSONObject) -> T): List<T> {
        val a = this ?: return emptyList()
        val out = ArrayList<T>(a.length())
        for (i in 0 until a.length()) a.optJSONObject(i)?.let { out.add(f(it)) }
        return out
    }

    // ─────────────────────────────────────────────────────────────────

    private suspend fun request(
        path: String,
        method: String,
        body: JSONObject?,
        withToken: Boolean = true
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        val base = prefs.serverUrl.trimEnd('/')
        if (base.isBlank()) return@withContext Result.Fail("Не указан адрес сервера")

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (withToken) prefs.token?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
            }

            val code = connection.responseCode
            val text = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use(BufferedReader::readText).orEmpty()

            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }

            if (code in 200..299) {
                Result.Ok(json)
            } else {
                Result.Fail(
                    message = humanError(code, json.optString("error")),
                    unauthorized = code == 401,
                    code = code,
                    error = json.optString("error")
                )
            }
        } catch (e: Exception) {
            // Сюда попадают обрыв связи, неверный адрес, выключенный сервер.
            // Текст исключения показывать пользователю бесполезно —
            // он на английском и про сокеты.
            Result.Fail("Сервер не отвечает. Проверьте адрес и сеть.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun humanError(code: Int, error: String?): String = when (error) {
        "bad_email" -> "Проверьте адрес почты"
        "password_too_short" -> "Пароль короче восьми знаков"
        "password_too_long" -> "Слишком длинный пароль"
        "email_taken" -> "На эту почту аккаунт уже есть — войдите"
        "bad_credentials" -> "Неверная почта или пароль"
        "too_many_attempts" -> "Слишком много попыток. Подождите пятнадцать минут"
        "registration_closed" -> "Регистрация на этом сервере закрыта"
        "too_many_registrations" -> "С этого интернета сегодня уже создавали аккаунты. Попробуйте завтра"
        "too_many_requests" -> "Слишком часто. Подождите минуту"
        "unauthorized" -> "Нужно войти заново"
        "bad_code" -> "Код не подошёл. Проверьте цифры из письма"
        "reset_expired" -> "Код устарел. Запросите письмо ещё раз"
        "code_expired" -> "Код устарел. Запросите новый"
        "same_email" -> "Это и так ваш адрес"
        "bad_password" -> "Пароль не подошёл"
        "too_large", "too_many_items" -> "Слишком много данных за один раз"
        "account_full" -> "В аккаунте слишком много записей"
        "server_full" -> "На сервере кончилось место. Данные остались на телефоне"
        "server_error" -> "Ошибка на сервере"
        // ── друзья ──
        "bad_handle" -> "Ник: латинские буквы, цифры и подчёркивание, " +
                "начиная с буквы, от трёх знаков до двадцати"
        "handle_taken" -> "Такой ник уже занят"
        "too_soon" -> "Ник можно менять раз в месяц"
        "self" -> "Это ваш собственный ник"
        "already_friends" -> "Вы уже друзья"
        "not_friends" -> "Он больше не у вас в друзьях"
        "friends_full" -> "Больше ста друзей приложение не держит"
        "too_many_pending" -> "Слишком много неотвеченных заявок. Уберите старые"
        "declined" -> "Заявку отклонили. Позвать снова можно через неделю"
        // ── тренер ──
        "bad_name" -> "Имя и фамилия — хотя бы три знака"
        "bad_gym" -> "Напишите, где вы тренируете"
        "not_coach" -> "Это доступно только тренерам"
        "not_coach_account" -> "Этот человек не объявлял себя тренером"
        else -> when (code) {
            401 -> "Нужно войти заново"
            404 -> "Адрес сервера указан неверно"
            else -> "Сервер ответил ошибкой ($code)"
        }
    }
}