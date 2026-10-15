package ru.styrk.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.styrk.app.data.local.database.AppDatabaseProvider
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncManager
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.common.ActionRow
import ru.styrk.app.ui.common.GhostButton
import ru.styrk.app.ui.common.RowAction
import ru.styrk.app.ui.common.WideButton
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Окно «Аккаунт»: вход, регистрация, состояние подключения.
 *
 * Аккаунт нужен ТОЛЬКО для копии данных на сервере. Без него приложение
 * работает полностью: и тренировки, и рекорды, и бэкап в файл. Поэтому
 * ничего здесь не навязывается и войти никто не заставляет.
 */
@Composable
fun AccountDialog(
    onDismiss: () -> Unit,
    // Данные приехали с сервера — экрану профиля надо перечитать себя.
    // Без этого обмен проходил успешно, а на экране всё оставалось
    // по-старому, и выглядело это как «ничего не подтянулось».
    onDataChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val scope = rememberCoroutineScope()

    var signedIn by remember { mutableStateOf(prefs.isSignedIn) }
    /* Сколько записей лежит НА ТЕЛЕФОНЕ. Считаем только когда человек
       вышел: пока он в учётной записи, стирать местное бессмысленно —
       оно вернётся с сервера при первом же обмене. */
    var here by remember { mutableStateOf<String?>(null) }
    var wiping by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf(prefs.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf(prefs.serverUrl) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var account by remember { mutableStateOf<SyncApi.Account?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Какой необратимый способ обмена подтверждается прямо сейчас.
    // null — ни один, обычное состояние.
    var confirmMode by remember { mutableStateOf<SyncManager.Mode?>(null) }
    // Открыт ли ящик с необратимыми действиями. Закрыт по умолчанию:
    // рядом с обычным обменом им не место.
    var showHard by remember { mutableStateOf(false) }
    // Показывать ли поле адреса сервера. По умолчанию спрятано:
    // адрес один и тот же для всех, а техническая строка в форме входа
    // заставляет думать, что надо что-то знать про серверы.
    var showServer by remember { mutableStateOf(false) }
    // Что нашлось по обе стороны — заполняется перед обменом, если
    // выяснилось, что и там и там есть свои тренировки
    var conflict by remember { mutableStateOf<SyncManager.Inspection?>(null) }
    // «Забыли пароль»: 0 — не начато, 1 — спросить письмо, 2 — ввести код.
    var forgot by remember { mutableStateOf(0) }
    var code by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    // Смена почты: 0 — не начата, 1 — новый адрес и пароль, 2 — код.
    var change by remember { mutableStateOf(0) }
    var newEmail by remember { mutableStateOf("") }

    // Когда уже вошли — сразу спрашиваем сервер, жив ли токен
    LaunchedEffect(signedIn) {
        if (signedIn) {
            when (val r = api.me()) {
                is SyncApi.Result.Ok -> account = r.data
                is SyncApi.Result.Fail -> {
                    // Токен мог протухнуть или аккаунт удалён с другого
                    // устройства. Данные на телефоне при этом не трогаем.
                    if (r.unauthorized) {
                        prefs.signOut(); signedIn = false
                        message = "Сессия истекла, войдите заново"
                    } else message = r.message
                }
            }
        }
    }

    fun run(block: suspend () -> Unit) {
        if (busy) return
        busy = true; message = null
        scope.launch { block(); busy = false }
    }

    // Развилка после входа: и на телефоне своё, и на сервере своё.
    //
    // Это единственное место, где вопрос уместен. Здесь человек ещё
    // помнит, что он только что сделал, и может ответить осмысленно:
    // «это мой старый аккаунт» или «я вошёл не туда». При обычном
    // обмене такого вопроса больше нет — там всегда объединение.
    conflict?.let { look ->
        SyncChoiceDialog(
            inspection = look,
            onDismiss = { conflict = null },
            onChoice = { chosen ->
                conflict = null
                // Что бы человек ни выбрал, вопрос закрыт: после любого
                // из трёх способов телефон и сервер приведены к согласию.
                // Спрашивать второй раз не о чем.
                prefs.email?.let { prefs.markReconciled(it) }
                run {
                    val db = AppDatabaseProvider.getDatabase(context)
                    when (val r = SyncManager(context, db).sync(chosen)) {
                        is SyncManager.Outcome.Ok -> {
                            message = when (chosen) {
                                SyncManager.Mode.MERGE ->
                                    "Объединено. Отправлено: ${r.sent}, получено: ${r.received}"
                                SyncManager.Mode.PUSH_OVERWRITE ->
                                    "Сервер приведён к виду телефона. Отправлено: ${r.sent}"
                                SyncManager.Mode.PULL_REPLACE ->
                                    "Данные телефона заменены. Получено: ${r.received}"
                            }
                            onDataChanged()
                        }
                        is SyncManager.Outcome.Fail -> message = r.message
                        SyncManager.Outcome.NotSignedIn -> message = "Сначала войдите"
                    }
                    (api.me() as? SyncApi.Result.Ok)?.let { account = it.data }
                }
            }
        )
    }

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(White)
                // Окно прокручивается: с раскрытыми «Особыми действиями»
                // оно выше экрана, и без прокрутки до «Свернуть» было
                // не добраться.
                .verticalScroll(rememberScrollState())
        ) {
            // ── Шапка ──
            // Тёмная полоса во всю ширину вместо заголовка внутри текста.
            // Так окно читается как отдельная страница, а не как записка.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Black)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    if (signedIn) "АККАУНТ" else "ВХОД",
                    color = White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (signedIn)
                        "Копия дневника на сервере. Те же тренировки открываются на любом вашем устройстве."
                    else
                        "Нужен только для копии на сервере. Без него приложение работает полностью.",
                    color = White.copy(.62f), fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            Column(Modifier.fillMaxWidth().padding(20.dp)) {

                if (signedIn) {
                    // Почта и три числа: сколько записей на сервере, с какого
                    // числа аккаунт и когда был последний обмен. Всё, что
                    // человеку нужно знать, чтобы понять — работает или нет.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(SoftGray, RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Text(prefs.email.orEmpty(), color = Black, fontSize = 15.sp,
                            fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth()) {
                            val since = account?.createdAt?.takeIf { it > 0 }?.let {
                                SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(it))
                            }
                            AccStat(account?.items?.toString() ?: "…", "записей", Modifier.weight(1f))
                            AccStat(since ?: "—", "с даты", Modifier.weight(1f))
                            AccStat(
                                prefs.lastSync.takeIf { it > 0 }?.let {
                                    SimpleDateFormat("dd.MM", Locale.getDefault()).format(Date(it))
                                } ?: "—",
                                "обмен", Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    WideButton(
                        text = if (busy) "СОЕДИНЯЮСЬ…" else "СИНХРОНИЗИРОВАТЬ",
                        enabled = !busy
                    ) {
                        run {
                            val db = AppDatabaseProvider.getDatabase(context)
                            val manager = SyncManager(context, db)

                            // Просто объединяем. Ничего не спрашиваем.
                            //
                            // Раньше здесь была разведка: если и на
                            // телефоне, и на сервере нашлось своё —
                            // всплывало окно с тремя кнопками. Но из этих
                            // трёх безопасна ровно одна. Объединение
                            // не теряет ничего, две другие стирают
                            // безвозвратно.
                            //
                            // Значит приложение при каждом обмене
                            // спрашивало, не хочет ли человек что-нибудь
                            // стереть. На такой вопрос через неделю
                            // отвечают не читая — и однажды промахиваются.
                            //
                            // Настоящая развилка одна и бывает один раз:
                            // сразу после входа в аккаунт. Она там
                            // и осталась. Здесь выбирать не из чего.
                            when (val r = manager.sync()) {
                                is SyncManager.Outcome.Ok -> {
                                    message = "Отправлено: ${r.sent}, получено: ${r.received}"
                                    if (r.received > 0) onDataChanged()
                                }
                                is SyncManager.Outcome.Fail -> message = r.message
                                SyncManager.Outcome.NotSignedIn -> message = "Сначала войдите"
                            }
                            // Обновляем счётчик записей на сервере
                            (api.me() as? SyncApi.Result.Ok)?.let { account = it.data }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Два необратимых способа лежат в закрытом ящике.
                    //
                    // Совсем убрать их нельзя: иногда человек ЗНАЕТ, что хочет
                    // привести сервер к виду телефона или наоборот, и обходного
                    // пути у него нет. Но и держать их рядом с обычным обменом
                    // нельзя — обе стирают безвозвратно, а кнопки рядом
                    // однажды путают.
                    //
                    // Поэтому: закрыто по умолчанию, открывается осознанно,
                    // и каждая ещё раз переспрашивает.
                    /* Под главной кнопкой — пара кнопок контуром:
                       «Особые действия» и «Выйти». Раньше это были строки
                       текста, и их не сразу узнавали как кнопки. */
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        GhostButton(
                            if (showHard) "СВЕРНУТЬ" else "ЕЩЁ",
                            enabled = !busy,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            if (showHard) {
                                showHard = false; confirmMode = null
                                change = 0; confirmDelete = false; password = ""
                            } else showHard = true
                        }
                        GhostButton(
                            "ВЫЙТИ", enabled = !busy,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            run {
                                api.logout()          // ответ не важен: гасим и локально
                                prefs.signOut()
                                signedIn = false; account = null; password = ""
                                showHard = false
                                message = "Вы вышли. Тренировки на телефоне остались на месте."
                            }
                        }
                    }

                    if (showHard) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Обычный обмен объединяет и ничего не теряет. " +
                                    "Эти два способа стирают — насовсем.",
                            color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(10.dp))

                        HardAction(
                            title = "Перезаписать сервер",
                            explain = "На сервере останется ровно то, что на телефоне. " +
                                    "Всё, чего здесь нет, будет удалено с сервера.",
                            armed = confirmMode == SyncManager.Mode.PUSH_OVERWRITE,
                            busy = busy,
                            onArm = { confirmMode = SyncManager.Mode.PUSH_OVERWRITE },
                            onCancel = { confirmMode = null },
                            onConfirm = {
                                confirmMode = null
                                run {
                                    val db = AppDatabaseProvider.getDatabase(context)
                                    message = when (val r =
                                        SyncManager(context, db).sync(SyncManager.Mode.PUSH_OVERWRITE)) {
                                        is SyncManager.Outcome.Ok ->
                                            "Сервер приведён к виду телефона. Отправлено: ${r.sent}"
                                        is SyncManager.Outcome.Fail -> r.message
                                        SyncManager.Outcome.NotSignedIn -> "Сначала войдите"
                                    }
                                    (api.me() as? SyncApi.Result.Ok)?.let { account = it.data }
                                }
                            }
                        )

                        Spacer(Modifier.height(8.dp))

                        HardAction(
                            title = "Заменить данными с сервера",
                            explain = "На телефоне останется ровно то, что на сервере. " +
                                    "Всё, чего нет на сервере, здесь пропадёт.",
                            armed = confirmMode == SyncManager.Mode.PULL_REPLACE,
                            busy = busy,
                            onArm = { confirmMode = SyncManager.Mode.PULL_REPLACE },
                            onCancel = { confirmMode = null },
                            onConfirm = {
                                confirmMode = null
                                run {
                                    val db = AppDatabaseProvider.getDatabase(context)
                                    message = when (val r =
                                        SyncManager(context, db).sync(SyncManager.Mode.PULL_REPLACE)) {
                                        is SyncManager.Outcome.Ok -> {
                                            onDataChanged()
                                            "Данные телефона заменены. Получено: ${r.received}"
                                        }
                                        is SyncManager.Outcome.Fail -> r.message
                                        SyncManager.Outcome.NotSignedIn -> "Сначала войдите"
                                    }
                                    (api.me() as? SyncApi.Result.Ok)?.let { account = it.data }
                                }
                            }
                        )

                        /* Редкое — здесь же, за «Особыми действиями»:
                           смена почты, адрес сервера, удаление аккаунта.
                           На виду остаются только обмен и выход — то,
                           ради чего окно открывают. */
                        Spacer(Modifier.height(10.dp))
                        /* ── Сменить почту ──

                           Код приходит на НОВЫЙ адрес: так проверяется, что
                           он настоящий и ваш. Без этого опечатка в адресе
                           отрезала бы от восстановления пароля. Текущий
                           пароль — потому что телефон мог оказаться в чужих
                           руках, а почта — это ключ от дневника. */
                        when (change) {
                            0 -> RowAction("Сменить почту", enabled = !busy) {
                                change = 1; newEmail = ""; password = ""
                            }
                            1 -> {
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = newEmail, onValueChange = { newEmail = it },
                                    label = { Text("Новая почта", color = DarkGray) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                                )
                                Spacer(Modifier.height(8.dp))
                                PasswordField(password, { password = it }, "Текущий пароль")
                                Spacer(Modifier.height(8.dp))
                                ActionRow {
                                    RowAction("Отмена", enabled = !busy) { change = 0; password = "" }
                                    RowAction("Прислать код", strong = true,
                                        enabled = !busy && newEmail.isNotBlank() && password.isNotBlank()) {
                                        run {
                                            when (val r = api.emailChangeStart(newEmail.trim(), password)) {
                                                is SyncApi.Result.Ok -> {
                                                    change = 2; code = ""; password = ""
                                                    message = "Код отправлен на ${newEmail.trim()}. " +
                                                            "Письма нет пару минут — загляните в «Спам»."
                                                }
                                                is SyncApi.Result.Fail -> message = r.message
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                Spacer(Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = code, onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                                    label = { Text("Код из письма", color = DarkGray) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                                )
                                Spacer(Modifier.height(8.dp))
                                ActionRow {
                                    RowAction("Отмена", enabled = !busy) { change = 0 }
                                    RowAction("Подтвердить", strong = true,
                                        enabled = !busy && code.length == 6) {
                                        run {
                                            when (val r = api.emailChangeConfirm(code)) {
                                                is SyncApi.Result.Ok -> {
                                                    prefs.email?.let { prefs.forgetReconciled(it) }
                                                    prefs.email = r.data
                                                    prefs.markReconciled(r.data)
                                                    email = r.data
                                                    change = 0
                                                    message = "Готово: теперь почта дневника — ${r.data}"
                                                    (api.me() as? SyncApi.Result.Ok)?.let { account = it.data }
                                                }
                                                is SyncApi.Result.Fail -> message = r.message
                                            }
                                        }
                                    }
                                }
                            }
                        }


                        Spacer(Modifier.height(4.dp))
                        if (!confirmDelete) {
                            // Удаление аккаунта обязано быть доступно из приложения —
                            // этого требуют магазины. Но выглядеть оно должно как
                            // то, что нажимают раз в жизни, а не как соседний пункт.
                            RowAction("Удалить аккаунт", enabled = !busy) {
                                confirmDelete = true
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Аккаунт и все данные на сервере будут удалены навсегда. " +
                                        "Тренировки на этом телефоне останутся. Введите пароль:",
                                color = Black, fontSize = 12.sp, lineHeight = 16.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            PasswordField(password, { password = it }, "Пароль")
                            Spacer(Modifier.height(8.dp))
                            ActionRow {
                                RowAction("Отмена", enabled = !busy, strong = true) {
                                    confirmDelete = false; password = ""
                                }
                                RowAction(
                                    "Удалить навсегда",
                                    enabled = !busy && password.isNotBlank()
                                ) {
                                    run {
                                        when (val r = api.deleteAccount(password)) {
                                            is SyncApi.Result.Ok -> {
                                                prefs.email?.let { prefs.forgetReconciled(it) }
                                                prefs.signOut()
                                                signedIn = false; confirmDelete = false
                                                password = ""; account = null
                                                message = "Аккаунт удалён"
                                            }
                                            is SyncApi.Result.Fail -> message = r.message
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text("сервер: " + prefs.serverUrl, color = DarkGray, fontSize = 11.sp)

                        Spacer(Modifier.height(6.dp))
                        RowAction("Свернуть", enabled = !busy) {
                            showHard = false; confirmMode = null
                            change = 0; confirmDelete = false; password = ""
                        }
                        Spacer(Modifier.height(6.dp))
                    }



                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Почта", color = DarkGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email, imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(12.dp))
                    PasswordField(password, { password = it }, "Пароль")
                    Spacer(Modifier.height(6.dp))
                    Text("Не короче восьми знаков", color = DarkGray, fontSize = 11.sp)

                    // Адрес сервера спрятан до тех пор, пока не понадобится.
                    if (showServer) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("Адрес сервера", color = DarkGray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = fieldColors()
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Свой сервер — только https, например https://api.styrk.ru. " +
                                "Адрес с http:// Android не пустит.",
                            color = DarkGray, fontSize = 11.sp
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    // Главное действие — во всю ширину и чёрным. Вторичное
                    // рядом с ним обычной строкой: так видно, что нажимать,
                    // не читая обе надписи.
                    WideButton(
                        text = if (busy) "СОЕДИНЯЮСЬ…" else "ВОЙТИ",
                        enabled = !busy && email.isNotBlank() && password.isNotBlank()
                    ) {
                        run {
                            prefs.serverUrl = serverUrl
                            when (val r = api.login(email.trim(), password)) {
                                is SyncApi.Result.Ok -> {
                                    prefs.token = r.data.token
                                    prefs.email = r.data.email
                                    password = ""; signedIn = true
                                    message = "Вход выполнен"

                                    // Единственный момент, когда вопрос
                                    // о расхождении осмыслен: человек
                                    // только что вошёл и помнит, зачем.
                                    //
                                    // Спрашиваем один раз на аккаунт.
                                    // Ответ запоминается, и при обычном
                                    // обмене больше не всплывает.
                                    val mail = r.data.email
                                    if (!prefs.isReconciled(mail)) {
                                        val db = AppDatabaseProvider.getDatabase(context)
                                        val look = SyncManager(context, db).inspect()
                                        // Спрашиваем, только если своё
                                        // есть с обеих сторон. Во всех
                                        // остальных случаях терять
                                        // нечего — объединяем молча.
                                        if (look.conflicting) conflict = look
                                        else prefs.markReconciled(mail)
                                    }
                                }
                                is SyncApi.Result.Fail -> message = r.message
                            }
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center) {
                        RowAction(
                            "Создать аккаунт",
                            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                            strong = true
                        ) {
                            run {
                                prefs.serverUrl = serverUrl
                                when (val r = api.register(email.trim(), password)) {
                                    is SyncApi.Result.Ok -> {
                                        prefs.token = r.data.token
                                        prefs.email = r.data.email
                                        password = ""; signedIn = true
                                        message = "Аккаунт создан"
                                        // Аккаунт только что создан — на сервере
                                        // пусто, расходиться не с чем. Помечаем
                                        // сразу, чтобы вопрос не всплыл потом
                                        // на ровном месте.
                                        prefs.markReconciled(r.data.email)
                                    }
                                    is SyncApi.Result.Fail -> message = r.message
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(20.dp,
                            Alignment.CenterHorizontally)) {
                        RowAction(
                            if (showServer) "Скрыть адрес сервера" else "Другой сервер",
                            enabled = !busy
                        ) { showServer = !showServer }
                        RowAction("Проверить связь", enabled = !busy) {
                            run {
                                prefs.serverUrl = serverUrl
                                message = when (api.health()) {
                                    is SyncApi.Result.Ok -> "Сервер отвечает"
                                    is SyncApi.Result.Fail -> "Сервер не отвечает по этому адресу"
                                }
                            }
                        }
                    }

                    /* ── Забыли пароль ──

                       Письмо со ссылкой и шестью цифрами. Ссылка откроет
                       страницу смены пароля в браузере; цифры вводятся
                       прямо здесь — для тех, у кого почта на компьютере. */
                    when (forgot) {
                        0 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            RowAction("Забыли пароль?", enabled = !busy) { forgot = 1 }
                        }
                        1 -> {
                            Spacer(Modifier.height(8.dp))
                            Text("Пришлём на почту выше письмо со ссылкой и кодом из шести цифр.",
                                color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp)
                            Spacer(Modifier.height(8.dp))
                            ActionRow {
                                RowAction("Отмена", enabled = !busy) { forgot = 0 }
                                RowAction("Прислать письмо", strong = true,
                                    enabled = !busy && email.isNotBlank()) {
                                    run {
                                        prefs.serverUrl = serverUrl
                                        when (val r = api.forgotPassword(email.trim())) {
                                            is SyncApi.Result.Ok -> {
                                                forgot = 2; code = ""; newPassword = ""
                                                message = "Если на ${email.trim()} есть дневник — письмо " +
                                                        "уже в пути. Нет пару минут — загляните в «Спам»."
                                            }
                                            is SyncApi.Result.Fail -> message = r.message
                                        }
                                    }
                                }
                            }
                        }
                        else -> {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = code, onValueChange = { if (it.length <= 6) code = it.filter(Char::isDigit) },
                                label = { Text("Код из письма", color = DarkGray) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(), colors = fieldColors()
                            )
                            Spacer(Modifier.height(8.dp))
                            PasswordField(newPassword, { newPassword = it }, "Новый пароль")
                            Spacer(Modifier.height(8.dp))
                            ActionRow {
                                RowAction("Отмена", enabled = !busy) { forgot = 0 }
                                RowAction("Сменить пароль", strong = true,
                                    enabled = !busy && code.length == 6 && newPassword.length >= 8) {
                                    run {
                                        when (val r = api.resetPassword(email.trim(), code, newPassword)) {
                                            is SyncApi.Result.Ok -> {
                                                prefs.token = r.data.token
                                                prefs.email = r.data.email
                                                newPassword = ""; password = ""; forgot = 0
                                                signedIn = true
                                                message = "Пароль изменён. На других устройствах " +
                                                        "войдите заново — прежние входы закрыты."
                                                val mail = r.data.email
                                                if (!prefs.isReconciled(mail)) {
                                                    val db = AppDatabaseProvider.getDatabase(context)
                                                    val look = SyncManager(context, db).inspect()
                                                    if (look.conflicting) conflict = look
                                                    else prefs.markReconciled(mail)
                                                }
                                            }
                                            is SyncApi.Result.Fail -> message = r.message
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                /* ── Стереть дневник с этого телефона ──────────────

                   Стоит здесь же, где вход, и только после выхода
                   из учётной записи. Так это и происходит у человека:
                   он вышел — и хочет, чтобы после него на общем или
                   чужом телефоне ничего не осталось. Идти для этого
                   в настройки Android и стирать данные приложения —
                   совет для другого человека.

                   Записи на сервере это не трогает, и так и написано:
                   пугать человека тем, чего кнопка не делает, — верный
                   способ, чтобы он не нажал её тогда, когда надо. */
                if (!signedIn) {
                    LaunchedEffect(signedIn) {
                        val db = AppDatabaseProvider.getDatabase(context)
                        val n = listOf(
                            db.workoutHistoryDao().getAllWorkouts().size to
                                Triple("тренировка", "тренировки", "тренировок"),
                            db.plannedWorkoutDao().getAll().size to
                                Triple("план", "плана", "планов"),
                            db.customExerciseDao().getAll().size to
                                Triple("своё упражнение", "своих упражнения", "своих упражнений"),
                            db.bodyMetricDao().getAll().size to
                                Triple("замер", "замера", "замеров")
                        ).filter { it.first > 0 }
                            .joinToString(" · ") { (c, w) -> "$c ${plural(c, w)}" }
                        here = n.takeIf { it.isNotBlank() }
                    }

                    here?.let { what ->
                        Spacer(Modifier.height(18.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(SoftGray, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            Text("Данные на этом телефоне", color = Black, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(what, color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Записи на сервере останутся: войдёте снова — они вернутся.",
                                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                            )
                            if (!wiping) {
                                RowAction("Удалить с устройства", enabled = !busy) {
                                    wiping = true
                                }
                            } else {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Будет удалено: $what. Это не отменить.",
                                    color = Black, fontSize = 12.sp, lineHeight = 16.sp
                                )
                                ActionRow {
                                    RowAction("Отмена", enabled = !busy, strong = true) {
                                        wiping = false
                                    }
                                    RowAction("Да, удалить", enabled = !busy) {
                                        run {
                                            val db = AppDatabaseProvider.getDatabase(context)
                                            SyncManager(context, db).wipeDevice()
                                            wiping = false
                                            here = null
                                            message = "Дневник на этом телефоне очищен."
                                            onDataChanged()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Black, fontSize = 12.sp, lineHeight = 16.sp)
                }
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Text("Соединяюсь…", color = DarkGray, fontSize = 12.sp)
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RowAction("Закрыть", enabled = !busy) { onDismiss() }
                }
            }   // конец внутреннего столбца с отступами
        }
    }
}

/**
 * Окно выбора, когда данные есть и на телефоне, и на сервере,
 * и они разные.
 *
 * Объединение стоит первым и выделено: оно ничего не теряет.
 * Два других способа что-то стирают безвозвратно, поэтому требуют
 * второго нажатия — случайно ткнуть пальцем в зале слишком легко.
 */
@Composable
private fun SyncChoiceDialog(
    inspection: SyncManager.Inspection,
    onDismiss: () -> Unit,
    onChoice: (SyncManager.Mode) -> Unit
) {
    var armed by remember { mutableStateOf<SyncManager.Mode?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(White, RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text("Данные разошлись", color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Только на телефоне: ${inspection.onlyHere} тренировок\n" +
                        "Только на сервере: ${inspection.onlyThere} тренировок",
                color = Black, fontSize = 13.sp, lineHeight = 18.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Одинаковые тренировки в счёт не идут — они узнаются по дате, " +
                        "длительности и составу подходов и склеятся сами.",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
            )

            Spacer(Modifier.height(16.dp))

            // ── Объединение: безопасный способ, поэтому главный ──
            WideButton(text = "ОБЪЕДИНИТЬ", enabled = true) {
                onChoice(SyncManager.Mode.MERGE)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Останется всё: и телефонные тренировки, и серверные. Ничего не теряется.",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
            )

            Spacer(Modifier.height(16.dp))

            ChoiceRow(
                title = "Перезаписать сервер",
                explain = "На сервере останется ровно то, что на телефоне. " +
                        "Серверные тренировки, которых здесь нет, будут удалены — " +
                        "и на других устройствах тоже.",
                armed = armed == SyncManager.Mode.PUSH_OVERWRITE,
                onClick = {
                    if (armed == SyncManager.Mode.PUSH_OVERWRITE) onChoice(SyncManager.Mode.PUSH_OVERWRITE)
                    else armed = SyncManager.Mode.PUSH_OVERWRITE
                }
            )

            Spacer(Modifier.height(12.dp))

            ChoiceRow(
                title = "Заменить данными с сервера",
                explain = "На телефоне останется ровно то, что на сервере. " +
                        "Тренировки, записанные только здесь, пропадут.",
                armed = armed == SyncManager.Mode.PULL_REPLACE,
                onClick = {
                    if (armed == SyncManager.Mode.PULL_REPLACE) onChoice(SyncManager.Mode.PULL_REPLACE)
                    else armed = SyncManager.Mode.PULL_REPLACE
                }
            )

            Spacer(Modifier.height(14.dp))
            Text(
                "Если не уверены — сохраните бэкап в файл. " +
                        "Он в этом же профиле, раздел «Данные».",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
            )

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RowAction("Отмена") { onDismiss() }
            }
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    explain: String,
    armed: Boolean,
    onClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(explain, color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(6.dp))
        RowAction(
            if (armed) "Нажмите ещё раз — действие необратимо" else "Выбрать",
            strong = armed
        ) { onClick() }
    }
}

/** Одно число с подписью в карточке аккаунта. */
/**
 * Необратимое действие: сначала объяснение, потом подтверждение.
 *
 * В один приём такие вещи не делаются. Нажатие «в один тап» на кнопку,
 * которая стирает дневник, — это ошибка, ждущая своего часа: рано или
 * поздно палец попадёт не туда, и вернуть будет нечего.
 *
 * Поэтому два шага. Первый называет действие, второй показывает
 * последствия словами и требует отдельного подтверждения. И «Отмена»
 * стоит первой — промахнуться в сторону безопасного проще, чем
 * в сторону разрушительного.
 */
@Composable
private fun HardAction(
    title: String,
    explain: String,
    armed: Boolean,
    busy: Boolean,
    onArm: () -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(title, color = Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(explain, color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp)

        if (!armed) {
            Spacer(Modifier.height(4.dp))
            RowAction("Выбрать", enabled = !busy, strong = true) { onArm() }
        } else {
            Spacer(Modifier.height(8.dp))
            Text("Это не отменить. Точно?", color = Black, fontSize = 12.sp,
                fontWeight = FontWeight.Bold)
            ActionRow {
                RowAction("Отмена", enabled = !busy, strong = true) { onCancel() }
                RowAction("Да, выполнить", enabled = !busy) { onConfirm() }
            }
        }
    }
}

/** «1 тренировка», «2 тренировки», «5 тренировок». */
private fun plural(n: Int, w: Triple<String, String, String>): String {
    val t = n % 10
    val h = n % 100
    return when {
        t == 1 && h != 11 -> w.first
        t in 2..4 && h !in 12..14 -> w.second
        else -> w.third
    }
}

@Composable
private fun AccStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = Black, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(3.dp))
        Text(label, color = DarkGray, fontSize = 10.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, color = DarkGray) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
        ),
        modifier = Modifier.fillMaxWidth(),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Black, unfocusedBorderColor = SoftGray, cursorColor = Black,
    focusedTextColor = Black, unfocusedTextColor = Black, focusedLabelColor = Black
)