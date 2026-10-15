package ru.styrk.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.common.ActionRow
import ru.styrk.app.ui.common.CheckRow
import ru.styrk.app.ui.common.RowAction
import ru.styrk.app.ui.common.WideButton
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Окно «Друзья».
 *
 * Три вещи и ничего сверх: свой ник, по которому вас находят; заявки;
 * список друзей с галочками — что каждому из них видно.
 *
 * Галочки стоят прямо на друге, а не общей настройкой сверху. Друзья
 * бывают разные: напарнику по залу показывают всё, знакомому — рекорды.
 * Одна галочка «показывать друзьям» заставила бы равняться на самого
 * нежеланного из них.
 *
 * Приняли заявку — не открыто ничего. Это осознанный выбор, а не
 * недоделка: сервис не решает за человека, что показывать. Чтобы это
 * не выглядело поломкой, у такого друга стоит пояснение прямо в строке.
 */
@Composable
fun FriendsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val signedIn = remember { prefs.isSignedIn }

    var info by remember { mutableStateOf<SyncApi.Friends?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var addText by remember { mutableStateOf("") }
    // Смена ника открывается по нажатию: обычно её не трогают, а поле
    // ввода рядом с ником звало бы менять его просто так.
    var renaming by remember { mutableStateOf(false) }
    var handleText by remember { mutableStateOf("") }
    // Кого убираем. Второе нажатие подтверждает: убрать друга — значит
    // и отобрать всё, что ему открыто, а обратно это одним тапом
    // не вернётся.
    var removing by remember { mutableStateOf<String?>(null) }
    // Кому сейчас показывается окно согласия «сделать тренером».
    var consenting by remember { mutableStateOf<SyncApi.Friend?>(null) }
    /* Какая строка раскрыта. Одна: у друга шесть строк подробностей,
       и вдесятером они превращают окно в простыню, по которой надо
       прокручивать, чтобы найти нужного. Свёрнутая строка — три
       строки: ник, кто он и что ему видно. */
    var opened by remember { mutableStateOf<String?>(null) }

    val reload: suspend () -> Unit = {
        when (val r = api.friends()) {
            is SyncApi.Result.Ok -> { info = r.data; Unit }
            is SyncApi.Result.Fail -> { message = r.message; Unit }
        }
    }

    fun act(block: suspend () -> SyncApi.Result<*>) {
        scope.launch {
            busy = true
            message = null
            when (val r = block()) {
                is SyncApi.Result.Fail -> message = r.message
                is SyncApi.Result.Ok -> {
                    /* Сначала общий сигнал, потом свой список.

                       Действие здесь меняет связи, а на них смотрят
                       и другие места: строка «написать тренеру»
                       в календаре, абонементы в профиле. Раньше они
                       узнавали об этом только при следующем запуске. */
                    Refresh.bump()
                    reload()
                }
            }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        if (signedIn) { busy = true; reload(); busy = false }
    }

    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.72).dp
    // Прокрутка списка живёт вне окна: отметили разрешение у друга —
    // список перечитался, а место, где вы были, осталось.
    val bodyScroll = rememberScrollState()

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(White)
        ) {
            // ── Шапка ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Black)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    "ДРУЗЬЯ", color = White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Сравнить рекорды и объём с тем, кого вы сами добавили. " +
                            "Пока вы не отметите — друг не видит о вас ничего.",
                    color = White.copy(.62f), fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBody)
                    .verticalScroll(bodyScroll)
                    .padding(20.dp)
            ) {

                if (!signedIn) {
                    Text(
                        "Друзья работают только с аккаунтом: ник и связи живут " +
                                "на сервере, а не на телефоне.",
                        color = Black, fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Войдите в профиле, в строке «Войти или зарегистрироваться», " +
                                "и возвращайтесь сюда.",
                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                    )
                } else {

                    // ── Свой ник ──
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(SoftGray, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        Text("ВАШ НИК", color = DarkGray, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            info?.handle?.let { "@$it" } ?: "…",
                            color = Black, fontSize = 20.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "По нему вас находят. Почту он не выдаёт.",
                            color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                        )

                        if (!renaming) {
                            RowAction("Изменить", enabled = !busy && info != null,
                                strong = true) {
                                handleText = info?.handle.orEmpty()
                                renaming = true
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = handleText,
                                onValueChange = { handleText = it.trim() },
                                label = { Text("Новый ник", color = DarkGray) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                                modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors()
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Латинские буквы, цифры и подчёркивание, начиная с буквы. " +
                                        "Менять можно раз в месяц — иначе по нику вас не найти.",
                                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                            )
                            ActionRow {
                                RowAction("Отмена", enabled = !busy) { renaming = false }
                                RowAction(
                                    "Сохранить",
                                    enabled = !busy && handleText.length >= 3,
                                    strong = true
                                ) {
                                    val h = handleText
                                    renaming = false
                                    act { api.setHandle(h) }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Позвать ──
                    Text("ПОЗВАТЬ В ДРУЗЬЯ", color = DarkGray, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = addText,
                        onValueChange = { addText = it.trim() },
                        label = { Text("Ник, например @kolya", color = DarkGray) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            focus.clearFocus()
                            val h = addText
                            if (h.length >= 3) { addText = ""; act { api.friendRequest(h) } }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Spacer(Modifier.height(8.dp))
                    WideButton(
                        text = "ПОЗВАТЬ",
                        enabled = !busy && addText.length >= 3
                    ) {
                        focus.clearFocus()
                        val h = addText
                        addText = ""
                        act { api.friendRequest(h) }
                    }

                    // ── Входящие ──
                    val incoming = info?.incoming.orEmpty()
                    if (incoming.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))
                        Text("ВАС ЗОВУТ", color = DarkGray, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        incoming.forEach { k ->
                            Spacer(Modifier.height(8.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .background(SoftGray, RoundedCornerShape(14.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Text("@${k.handle}", color = Black, fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(2.dp))
                                Text(whenText(k.at), color = DarkGray, fontSize = 11.sp)
                                ActionRow {
                                    RowAction("Принять", enabled = !busy, strong = true) {
                                        act { api.friendAccept(k.handle) }
                                    }
                                    RowAction("Отклонить", enabled = !busy) {
                                        act { api.friendDecline(k.handle) }
                                    }
                                }
                            }
                        }
                    }

                    // ── Друзья ──
                    val friends = info?.friends.orEmpty()
                    Spacer(Modifier.height(20.dp))
                    Text("ВАШИ ДРУЗЬЯ", color = DarkGray, fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.height(8.dp))

                    if (friends.isEmpty()) {
                        Text(
                            if (busy) "…"
                            else "Пока никого. Спросите у знакомого его ник и позовите.",
                            color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    } else {
                        friends.forEach { f ->
                            FriendRow(
                                friend = f,
                                busy = busy,
                                arming = removing == f.handle,
                                open = opened == f.handle,
                                onToggle = {
                                    opened = if (opened == f.handle) null else f.handle
                                    removing = null
                                },
                                onShare = { share -> act { api.friendShare(f.handle, share) } },
                                onAskCoach = { consenting = f },
                                onArmRemove = { removing = if (removing == f.handle) null else f.handle },
                                onRemove = {
                                    removing = null
                                    act { api.friendRemove(f.handle) }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }

                    // ── Исходящие ──
                    val outgoing = info?.outgoing.orEmpty()
                    if (outgoing.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text("ЖДУТ ОТВЕТА", color = DarkGray, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        outgoing.forEach { k ->
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("@${k.handle}", color = Black, fontSize = 13.sp)
                                    Text(whenText(k.at), color = DarkGray, fontSize = 11.sp)
                                }
                                RowAction("Отменить", enabled = !busy) {
                                    act { api.friendRemove(k.handle) }
                                }
                            }
                        }
                    }
                }

                message?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, color = Black, fontSize = 12.sp, lineHeight = 17.sp)
                }

                consenting?.let { f ->
                    CoachConsentDialog(
                        friend = f,
                        onDismiss = { consenting = null },
                        onGrant = { until ->
                            consenting = null
                            act {
                                api.friendShare(f.handle,
                                    f.share.copy(coach = true, until = until))
                            }
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RowAction("Закрыть", enabled = !busy, strong = true) { onDismiss() }
                }
            }
        }
    }
}

/**
 * Окно согласия «сделать тренером».
 *
 * Список прав написан целиком и словами: человек отдаёт не «доступ»,
 * а понятные вещи, и увидеть их он должен ДО, а не после.
 */
@Composable
private fun CoachConsentDialog(
    friend: SyncApi.Friend,
    onDismiss: () -> Unit,
    onGrant: (Long?) -> Unit
) {
    val c = friend.coach ?: return
    /* Срок вместо выбора даты.

       Календарь здесь был бы честнее и хуже: человек не знает, какое
       число написать, и выбирает наугад. «Три месяца» он понимает
       сразу, а перевести это в число — наша работа, не его.

       Пусто по умолчанию: срок нужен не всем, и навязывать его,
       заставляя снимать, — плохой обмен. */
    var days by remember { mutableStateOf(0) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(White)
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.8).dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
        ) {
            Text("Сделать тренером", color = Black, fontSize = 16.sp,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SoftGray, RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Text(c.name, color = Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    listOfNotNull(c.gym.takeIf { it.isNotBlank() }, "@${friend.handle}")
                        .joinToString(" · "),
                    color = DarkGray, fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("Что он получит", color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(
                "видеть все ваши тренировки: упражнения, подходы, веса, даты",
                "видеть ваши рекорды и объём",
                "видеть ваш календарь: что запланировано и что выполнено",
                "назначать вам тренировки — они появятся в вашем календаре, " +
                        "и вы сможете согласиться или отказаться"
            ).forEach { Bullet(it) }

            Spacer(Modifier.height(12.dp))
            Text("Чего он не получит", color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            listOf(
                "замеры тела, вес и снимок — это отдельная галочка, и по умолчанию она снята",
                "возможность писать вам в приложении: переписки здесь нет",
                "возможность что-либо изменить в ваших записях"
            ).forEach { Bullet(it) }

            Spacer(Modifier.height(14.dp))
            Text("НА КАКОЙ СРОК", color = DarkGray, fontSize = 10.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Бессрочно", 90 to "3 месяца", 30 to "Месяц").forEach { (d, title) ->
                    val on = days == d
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (on) Black else SoftGray)
                            .clickable { days = d }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(title, color = if (on) White else Black, fontSize = 12.sp,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.Center)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Срок нужен затем, что из зала уходят и отозвать доступ забывают: " +
                        "забывчивость чинится только сроком.",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "Отозвать можно в любой момент и одним нажатием — здесь же, " +
                        "в строке этого друга.",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
            )

            Spacer(Modifier.height(16.dp))
            /* Высота ряда — по самой высокой кнопке.

               Без этого «ДА, ОН МОЙ ТРЕНЕР» переносится на две строки
               и становится выше соседа, а «НЕ СЕЙЧАС» остаётся низкой.
               Ряд из двух кнопок разной высоты читается как сбитая
               вёрстка — ровно это и было видно на снимке.

               IntrinsicSize.Min меряет ряд по содержимому,
               fillMaxHeight растягивает обе до общей высоты. Правило
               то же, что для ширины: кнопки в ряду — один орган
               управления, и разниться им не с чего. */
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WideButton(
                    text = "НЕ СЕЙЧАС", enabled = true,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) { onDismiss() }
                WideButton(
                    text = "ДА, ОН МОЙ ТРЕНЕР", enabled = true,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    onGrant(
                        if (days == 0) null
                        else java.time.LocalDate.now().plusDays(days.toLong()).toEpochDay()
                    )
                }
            }
        }
    }
}

/** Одна строка вместо шести: кто он вам и что ему видно. */
private fun shortWord(f: SyncApi.Friend): String {
    val seen = listOfNotNull(
        "рекорды".takeIf { f.share.records },
        "объём".takeIf { f.share.volume },
        "вес".takeIf { f.share.weight }
    )
    val what = (if (seen.isEmpty()) "не видит ничего" else "видит " + seen.joinToString(", ")) +
            (if (f.share.notify) " · узнаёт о тренировках" else "")
    return if (f.share.coach) "ваш тренер · $what" else what
}

/** Галочка права и «?» к ней: что именно получит друг. */
@Composable
private fun PermRow(
    text: String,
    checked: Boolean,
    enabled: Boolean,
    onInfo: () -> Unit,
    onChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            CheckRow(text = text, checked = checked, enabled = enabled, onChange = onChange)
        }
        InfoMark(onInfo)
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("—", color = Gold, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Black, fontSize = 12.sp, lineHeight = 17.sp)
    }
}

/**
 * Один друг: ник, две галочки и «убрать».
 *
 * Галочки означают «что видит он», а не «что вижу я». Это разные вещи,
 * и путать их нельзя: человек, отметивший «рекорды», решает показать
 * свои, а не посмотреть чужие. Поэтому под ними написано отдельной
 * строкой, что открыто в обратную сторону.
 */
@Composable
private fun FriendRow(
    friend: SyncApi.Friend,
    busy: Boolean,
    arming: Boolean,
    open: Boolean,
    onToggle: () -> Unit,
    onShare: (SyncApi.Share) -> Unit,
    onAskCoach: () -> Unit,
    onArmRemove: () -> Unit,
    onRemove: () -> Unit
) {
    // Какое право сейчас объясняем в окне «?».
    var info by remember { mutableStateOf<String?>(null) }
    info?.let { key -> PermissionInfoDialog(key) { info = null } }

    Column(
        Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        /* Вся шапка нажимается, а не стрелка в шесть точек: человек
           целится в имя, а не в значок. */
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = !busy) { onToggle() }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "@${friend.handle}", color = Black, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                friend.coach?.let { c ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "тренер · ${c.name}" + if (c.gym.isNotBlank()) " · ${c.gym}" else "",
                        color = Gold, fontSize = 11.sp, lineHeight = 15.sp
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(shortWord(friend), color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (open) "⌃" else "⌄", color = DarkGray, fontSize = 14.sp)
        }

        // Подробности — только у раскрытой строки.
        if (open) {

            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                RowAction(if (arming) "Не убирать" else "Убрать", enabled = !busy) {
                    onArmRemove()
                }
            }

            if (arming) {
                Text(
                    "Уберём и его, и всё, что ему открыто. Позвать снова можно, " +
                            "но галочки придётся ставить заново.",
                    color = Black, fontSize = 12.sp, lineHeight = 16.sp
                )
                RowAction("Да, убрать", enabled = !busy, strong = true) { onRemove() }
            } else {

                /* Право «он мой тренер» — не галочка в одном ряду
                   с «рекордами». Те про показать; это про доверить: смотреть
                   весь дневник и назначать за меня тренировки. Мешать их
                   в один список значило бы позволить отдать доверие,
                   целясь в показ. */
                if (friend.share.coach) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(White, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Ваш тренер", color = Black, fontSize = 13.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            InfoMark { info = "coach" }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Видит ваш дневник целиком и планирует за вас тренировки." +
                                    (friend.share.until?.let {
                                        val d = runCatching {
                                            java.time.LocalDate.ofEpochDay(it)
                                                .format(java.time.format.DateTimeFormatter
                                                    .ofPattern("d MMMM",
                                                        java.util.Locale.forLanguageTag("ru")))
                                        }.getOrDefault("")
                                        if (d.isNotBlank()) " Доступ до $d." else ""
                                    } ?: ""),
                            color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        PermRow(
                            text = "Показывать ещё и замеры тела",
                            checked = friend.share.body,
                            enabled = !busy,
                            onInfo = { info = "body" }
                        ) { onShare(friend.share.copy(body = it)) }
                        RowAction("Отозвать доверие", enabled = !busy) {
                            onShare(friend.share.copy(coach = false, body = false))
                        }
                    }
                } else if (friend.coach != null) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) {
                            RowAction("Сделать моим тренером", enabled = !busy, strong = true) {
                                onAskCoach()
                            }
                        }
                        InfoMark { info = "coach" }
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("ОН ВИДИТ", color = DarkGray, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))

                PermRow(
                    text = "Рекорды",
                    checked = friend.share.records,
                    enabled = !busy,
                    onInfo = { info = "records" }
                ) { onShare(friend.share.copy(records = it)) }

                Spacer(Modifier.height(6.dp))

                PermRow(
                    text = "Объём за неделю",
                    checked = friend.share.volume,
                    enabled = !busy,
                    onInfo = { info = "volume" }
                ) { onShare(friend.share.copy(volume = it)) }

                Spacer(Modifier.height(6.dp))

                /* Вес — отдельная галочка, не «рекорды» и не «тело».
                   Рекорды говорят, сколько человек жмёт; вес — сколько он
                   весит, и это скрывают чаще. А замеры со снимком уходят
                   только тренеру. */
                PermRow(
                    text = "График веса",
                    checked = friend.share.weight,
                    enabled = !busy,
                    onInfo = { info = "weight" }
                ) { onShare(friend.share.copy(weight = it)) }
                Text(
                    "Только линия веса на весах. Замеры и снимок сюда не входят — " +
                            "это отдельное согласие и только тренеру.",
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )

                /* Не «он видит», а «ему пишем»: это не показ, а извещение.
                   В одном списке с показом его легко включить, не заметив,
                   что теперь человеку будут приходить сообщения. */
                Spacer(Modifier.height(10.dp))
                Text("ЕМУ ПИШЕМ", color = DarkGray, fontSize = 9.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                PermRow(
                    text = "Когда я начал и закончил тренировку",
                    checked = friend.share.notify,
                    enabled = !busy,
                    onInfo = { info = "notify" }
                ) { onShare(friend.share.copy(notify = it)) }
                Text(
                    "Сообщение в Телеграм-бот STYRK — если у него привязан Телеграм.",
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )

                if (friend.share.noneAtAll) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Сейчас он не видит о вас ничего. Так и задумано — пока " +
                                "вы не отметите сами.",
                        color = Gold, fontSize = 11.sp, lineHeight = 15.sp
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    theirWord(friend.theirShare),
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
        }
    }
}

/** Что друг открыл в обратную сторону — одной строкой. */
private fun theirWord(t: SyncApi.Share): String {
    val opened = listOfNotNull(
        "рекорды".takeIf { t.records },
        "объём".takeIf { t.volume },
        "график веса".takeIf { t.weight }
    )
    val head = if (opened.isEmpty()) "" else "Он открыл вам " + when (opened.size) {
        1 -> opened[0]
        else -> opened.dropLast(1).joinToString(", ") + " и " + opened.last()
    }
    return when {
        head.isEmpty() && !t.notify -> "Он вам пока ничего не открыл"
        head.isEmpty() -> "Он сообщает вам о своих тренировках"
        t.notify -> "$head и сообщает о тренировках"
        else -> head
    }
}

/** «сегодня» или «12.08.26» — точное время тут ничего не решает. */
private fun whenText(ms: Long): String {
    if (ms <= 0L) return ""
    val day = 86_400_000L
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < day -> "сегодня"
        diff < 2 * day -> "вчера"
        else -> SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(Date(ms))
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Black, unfocusedBorderColor = LightGray, cursorColor = Black,
    focusedTextColor = Black, unfocusedTextColor = Black, focusedLabelColor = Black
)
