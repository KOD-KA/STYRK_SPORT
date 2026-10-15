package ru.styrk.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.common.MarkBox
import ru.styrk.app.ui.common.RowAction
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Зов на совместную тренировку.
 *
 * Зов везёт КОПИЮ плана, а не ссылку на него.
 *
 * Общий план на двоих был бы удобнее на словах и хуже на деле: это та
 * же развилка синхронизации, только вдвоём. Двое правят одну запись
 * с разных устройств, и кто-то однажды обнаружит, что его правку
 * стёрли, — а разбираться будет некому.
 *
 * Копия ведёт себя понятно: позвали, принял — план лёг ему в календарь
 * и дальше его. Добавил упражнение — у зовущего ничего не поменялось.
 * Это ровно то, что происходит в зале.
 */

private val DAY_MONTH: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

/**
 * Общий запас зовов на всё приложение.
 *
 * Он нужен затем, что зовы теперь показываются в ДВУХ местах сразу:
 * точкой в сетке месяца и карточкой под выбранным днём. Если бы каждое
 * место спрашивало сервер само, телефон делал бы два одинаковых запроса
 * и мог получить на них разные ответы — точка стояла бы на дне, где
 * карточки нет, и наоборот.
 */
object InviteFeed {
    var data: SyncApi.Invites? by mutableStateOf(null)
        internal set

    /**
     * Дни, на которые тренировка правда совместная.
     *
     * Отказы сюда НЕ входят, и это главное. Раньше входили — и день,
     * на который человека позвали, а он отказался, оставался помеченным
     * навсегда. Метка обещала совместную тренировку там, где её точно
     * не будет; это хуже, чем отсутствие метки.
     *
     * Остаются два случая: договорились («придёт») и ещё не ответили —
     * второе требует действия и потерять его нельзя.
     */
    val days: Set<Long>
        get() = data?.let { d ->
            (d.incoming + d.outgoing)
                .filter { it.status != "declined" }
                .map { it.plan.date }
                .toSet()
        } ?: emptySet()

    /** Входящие, на которые ещё не ответили, — их терять нельзя. */
    fun pendingDays(): List<Long> = data?.incoming.orEmpty()
        .filter { it.status == "pending" }
        .map { it.plan.date }
        .distinct()
        .sorted()
}

/**
 * Невидимый загрузчик. Ставится один раз на экране календаря.
 *
 * Сам ничего не рисует: его дело — сходить на сервер и сложить ответ
 * в [InviteFeed], откуда его возьмут и сетка, и карточка дня.
 */
@Composable
fun InviteWatch() {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val signedIn = remember { prefs.isSignedIn }
    LaunchedEffect(signedIn, Refresh.version) {
        if (!signedIn) return@LaunchedEffect
        when (val r = api.invites()) {
            is SyncApi.Result.Ok -> InviteFeed.data = r.data
            // Молча: календарь работает и без сервера, и красная строка
            // над ним из-за пропавшей сети — плохой обмен.
            is SyncApi.Result.Fail -> Unit
        }
    }
}

/**
 * Зовы ВЫБРАННОГО ДНЯ — под календарём.
 *
 * Как было и почему это плохо. Блок висел над календарём и показывал
 * сразу все зовы, какие есть. Человек открывал любое число месяца —
 * и над сеткой стояло «@вася зовёт на тренировку 8 сентября»,
 * независимо от того, какое число выбрано. Сообщение о конкретном дне,
 * которое видно во все дни, читается как поломка: либо оно не про этот
 * день, либо календарь врёт.
 *
 * Стало: зов показывается на своём числе и под календарём — там же,
 * где тренировка этого дня и план этого дня. Один день — одно место.
 *
 * Чтобы неотвеченный зов при этом нельзя было не заметить, в сетке
 * у такого числа стоит точка, а под календарём — строка «вас зовут
 * ещё на…» с числами, по которым можно перейти одним касанием.
 */
@Composable
fun InviteDay(
    day: LocalDate,
    onAccepted: (SyncApi.InvitePlan) -> Unit,
    onJump: (LocalDate) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val scope = rememberCoroutineScope()
    val signedIn = remember { prefs.isSignedIn }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var declining by remember { mutableStateOf<SyncApi.Invite?>(null) }

    if (!signedIn) return
    val data = InviteFeed.data ?: return

    val epoch = day.toEpochDay()
    val incoming = data.incoming.filter { it.plan.date == epoch }
    val outgoing = data.outgoing.filter { it.plan.date == epoch }
    val elsewhere = InviteFeed.pendingDays().filter { it != epoch }
    if (incoming.isEmpty() && outgoing.isEmpty() && elsewhere.isEmpty()) return

    fun answer(inv: SyncApi.Invite, yes: Boolean, note: String) {
        scope.launch {
            busy = true
            message = null
            when (val r = api.inviteAnswer(inv.id, yes, note)) {
                is SyncApi.Result.Fail -> message = r.message
                is SyncApi.Result.Ok -> {
                    r.data?.let(onAccepted)
                    // Ответили — значит связи могли измениться:
                    // пусть перечитают и соседние места.
                    Refresh.bump()
                    when (val again = api.invites()) {
                        is SyncApi.Result.Ok -> InviteFeed.data = again.data
                        is SyncApi.Result.Fail -> Unit
                    }
                }
            }
            busy = false
        }
    }

    Column(Modifier.fillMaxWidth()) {

        // ── ВАС ЗОВУТ ────────────────────────────────────────────────
        incoming.forEach { inv ->
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Black)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                /* Золотая подпись, а не серая: это единственная карточка
                   в календаре, которая чего-то ждёт от человека. */
                Text(
                    if (inv.fromCoach) "НАЗНАЧЕНО ТРЕНЕРОМ" else "ВАС ЗОВУТ",
                    color = Gold, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "@" + inv.handle,
                    color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    planWhen(inv.plan),
                    color = White.copy(alpha = 0.6f), fontSize = 12.sp, lineHeight = 16.sp
                )
                if (inv.plan.exercises.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        inv.plan.exercises.take(6).joinToString(", ") +
                            if (inv.plan.exercises.size > 6)
                                " и ещё ${inv.plan.exercises.size - 6}" else "",
                        color = White.copy(alpha = 0.45f), fontSize = 11.sp, lineHeight = 15.sp
                    )
                }
                /* Заметка тренера — не то же, что причина отказа.
                   Первая идёт до тренировки и от тренера, вторая
                   после и обратно. Показываем её отдельным полем,
                   чтобы её нельзя было прочесть как что-то своё. */
                if (inv.planNote.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(White.copy(alpha = 0.08f))
                            .padding(10.dp)
                    ) {
                        Text("ЗАМЕТКА ТРЕНЕРА", color = White.copy(alpha = 0.45f), fontSize = 9.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(inv.planNote, color = White, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DarkPill("ПРИДУ", filled = true, enabled = !busy,
                        modifier = Modifier.weight(1f).fillMaxHeight()) {
                        answer(inv, true, "")
                    }
                    DarkPill("НЕ СМОГУ", filled = false, enabled = !busy,
                        modifier = Modifier.weight(1f).fillMaxHeight()) {
                        declining = inv
                    }
                }
            }
        }

        // ── ВМЕСТЕ (кого позвали вы) ─────────────────────────────────
        if (outgoing.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SoftGray)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Text("ВМЕСТЕ", color = DarkGray, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                outgoing.forEachIndexed { idx, inv ->
                    if (idx > 0) Spacer(Modifier.height(4.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "@" + inv.handle,
                                color = Black, fontSize = 14.sp, fontWeight = FontWeight.Bold
                            )
                            Text(
                                when (inv.status) {
                                    "accepted" -> "придёт"
                                    "declined" -> "не сможет" +
                                        if (inv.note.isNotBlank()) " · " + inv.note else ""
                                    else -> "ещё не ответил"
                                } + " · " + "%02d:%02d".format(inv.plan.hour, inv.plan.minute),
                                color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp
                            )
                        }
                        if (inv.status == "pending") {
                            RowAction("Отозвать", enabled = !busy) {
                                scope.launch {
                                    busy = true
                                    when (val r = api.inviteCancel(inv.id)) {
                                        is SyncApi.Result.Fail -> message = r.message
                                        is SyncApi.Result.Ok ->
                                            when (val again = api.invites()) {
                                                is SyncApi.Result.Ok -> InviteFeed.data = again.data
                                                is SyncApi.Result.Fail -> Unit
                                            }
                                    }
                                    busy = false
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Зовут ещё на другие числа ────────────────────────────────
        //
        // Ради этой строки всё и затевалось осторожно: зов, спрятанный
        // в свой день, можно не найти, если не знать, куда смотреть.
        // Числа здесь нажимаются и переносят календарь на нужный день.
        if (elsewhere.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gold.copy(alpha = 0.12f))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Gold))
                Spacer(Modifier.width(9.dp))
                Text(
                    "Вас зовут ещё на " + elsewhere.size + " " +
                        dayWord(elsewhere.size) + ":",
                    color = Black, fontSize = 12.sp
                )
                Spacer(Modifier.width(6.dp))
                elsewhere.take(4).forEach { e ->
                    val d = runCatching { LocalDate.ofEpochDay(e) }.getOrNull()
                    if (d != null) {
                        Text(
                            d.format(DAY_MONTH),
                            color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { onJump(d) }
                                .padding(horizontal = 4.dp)
                        )
                    }
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = Black, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }

    declining?.let { inv ->
        DeclineDialog(
            handle = inv.handle,
            onDismiss = { declining = null },
            onSend = { note ->
                declining = null
                answer(inv, false, note)
            }
        )
    }
}

private fun dayWord(n: Int): String {
    val a = n % 100
    if (a in 11..14) return "дат"
    return when (a % 10) {
        1 -> "дату"
        2, 3, 4 -> "даты"
        else -> "дат"
    }
}

/**
 * Кнопка на чёрной карточке.
 *
 * Обычный [Pill] рассчитан на светлую подложку: у него чёрная рамка
 * и чёрный текст, на чёрном он был бы невидим.
 */
@Composable
private fun DarkPill(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (filled) White else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (filled) Color.Transparent else White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (filled) Black else White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

/**
 * «Когда мне удобно» — строка тренеру своими словами.
 *
 * Не форма с днями недели и часами: разбирать её на поля значило бы
 * заставить человека заполнять таблицу там, где хватает фразы
 * «по будням после семи». Тренер всё равно прочтёт её глазами.
 *
 * Строка появляется, только когда у человека есть тренер. Без него
 * писать некому, и место она занимала бы зря.
 */
@Composable
fun CoachWishRow() {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val scope = rememberCoroutineScope()
    val signedIn = remember { prefs.isSignedIn }

    var coaches by remember { mutableStateOf<List<SyncApi.Friend>>(emptyList()) }
    var writing by remember { mutableStateOf<SyncApi.Friend?>(null) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    /* Перечитываем и при изменении связей, а не только при первом
       показе. Иначе так: человек только что сделал друга тренером —
       и не видит строку «написать тренеру», пока не выйдет и не зайдёт
       заново. */
    LaunchedEffect(signedIn, Refresh.version) {
        if (!signedIn) return@LaunchedEffect
        when (val r = api.friends()) {
            is SyncApi.Result.Ok -> coaches = r.data.friends.filter { it.share.coach }
            is SyncApi.Result.Fail -> Unit
        }
    }

    if (coaches.isEmpty()) return

    /* Строка показывает то, что СЕЙЧАС лежит у тренера. Пожелание
       есть — видно его текст; убрали — строка снова зовёт написать.
       Раньше она всегда звала «написать», и после «убрать» человек
       не видел никакой разницы: казалось, что ничего не убралось. */
    Column(Modifier.fillMaxWidth()) {
        coaches.forEach { f ->
            val wish = f.myWish
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SoftGray, RoundedCornerShape(12.dp))
                    .clickable(enabled = !busy) { writing = f; text = wish ?: "" }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (wish != null) "Удобно: $wish" else "Написать тренеру, когда мне удобно",
                        color = Black, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 2
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "@${f.handle}" + (f.coach?.name?.let { " · $it" } ?: "") +
                                (if (wish != null) " · изменить" else ""),
                        color = DarkGray, fontSize = 11.sp
                    )
                }
                Text("›", color = DarkGray, fontSize = 18.sp)
            }
            Spacer(Modifier.height(10.dp))
        }
        message?.let {
            Text(it, color = Black, fontSize = 12.sp, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
        }
    }

    /* Сообщение об ошибке висит недолго: это не состояние, а отклик. */
    LaunchedEffect(message) {
        if (message != null) { kotlinx.coroutines.delay(4000); message = null }
    }

    writing?.let { f ->
        Dialog(onDismissRequest = { if (!busy) writing = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(White)
                    .padding(20.dp)
            ) {
                Text("Когда мне удобно", color = Black, fontSize = 16.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Одной строкой, своими словами. Тренер получит сообщение " +
                            "и увидит это у себя в карточке.",
                    color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 300) text = it },
                    label = { Text("Например: по будням после семи", color = DarkGray) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Black, unfocusedBorderColor = LightGray,
                        cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
                    )
                )
                Spacer(Modifier.height(14.dp))
                /* «Убрать» — только когда есть что убирать. */
                fun setWish(v: String?) {
                    coaches = coaches.map { if (it.handle == f.handle) it.copy(myWish = v) else it }
                }
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (f.myWish != null) {
                        Pill("УБРАТЬ", filled = false, enabled = !busy, modifier = Modifier.weight(1f).fillMaxHeight()) {
                            scope.launch {
                                busy = true
                                when (val r = api.wish(f.handle, "")) {
                                    is SyncApi.Result.Fail -> message = r.message
                                    is SyncApi.Result.Ok -> setWish(null)
                                }
                                busy = false; writing = null
                            }
                        }
                    }
                    Pill("ОТПРАВИТЬ", filled = true, enabled = !busy && text.isNotBlank(),
                            modifier = Modifier.weight(1f).fillMaxHeight()) {
                        val t = text.trim().replace(Regex("\\s+"), " ")
                        scope.launch {
                            busy = true
                            when (val r = api.wish(f.handle, t)) {
                                is SyncApi.Result.Fail -> message = r.message
                                is SyncApi.Result.Ok -> setWish(t)
                            }
                            busy = false; writing = null
                        }
                    }
                }
            }
        }
    }
}

/**
 * Отказ с причиной.
 *
 * Причина необязательна: заставлять человека объясняться, чтобы
 * отказаться, — плохой обмен. Но место для неё есть, потому что
 * «не смогу» без причины зовущий всё равно переспросит.
 */
@Composable
private fun DeclineDialog(
    handle: String,
    onDismiss: () -> Unit,
    onSend: (String) -> Unit
) {
    var note by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(White)
                .padding(20.dp)
        ) {
            Text("Не смогу", color = Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Можно написать почему — придёт вместе с отказом. Можно не писать.",
                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { if (it.length <= 300) note = it },
                label = { Text("Например: работаю до девяти", color = DarkGray) },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Black, unfocusedBorderColor = LightGray,
                    cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
                )
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("ОТМЕНА", filled = false, enabled = true, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    onDismiss()
                }
                Pill("ОТКАЗАТЬСЯ", filled = true, enabled = true, modifier = Modifier.weight(1f).fillMaxHeight()) {
                    onSend(note.trim())
                }
            }
            Text(
                "@$handle увидит отказ там же, где звал.",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}

/**
 * Складка «позвать друга» в окне планирования.
 *
 * Закрыта по умолчанию и стоит последней. Большинство тренируется
 * в одиночку, и раскрытый список друзей посреди окна планирования
 * мешал бы им каждый раз.
 */
@Composable
fun InviteFold(selected: MutableList<String>) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val signedIn = remember { prefs.isSignedIn }

    var open by remember { mutableStateOf(false) }
    var friends by remember { mutableStateOf<List<String>?>(null) }

    if (!signedIn) return

    // Список друзей тянем только когда складку раскрыли. Большинство
    // её не раскроет никогда, и ходить за ним при каждом открытии окна
    // планирования незачем.
    LaunchedEffect(open) {
        if (open && friends == null) {
            friends = when (val r = api.friends()) {
                is SyncApi.Result.Ok -> r.data.friends.map { it.handle }
                is SyncApi.Result.Fail -> emptyList()
            }
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SoftGray, RoundedCornerShape(12.dp))
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (open) "▾" else "▸", fontSize = 13.sp, color = DarkGray)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Позвать друга", color = Black, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text(
                    if (selected.isEmpty()) "необязательно — тренируетесь вдвоём?"
                    else "позовём ${selected.size}",
                    color = DarkGray, fontSize = 12.sp
                )
            }
        }

        if (open) {
            Spacer(Modifier.height(10.dp))
            val list = friends
            if (list == null) {
                Text("…", color = DarkGray, fontSize = 12.sp)
            } else if (list.isEmpty()) {
                Text(
                    "Друзей пока нет. Профиль → Друзья — там можно позвать по нику.",
                    color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                )
            } else {
                Text(
                    "Ему придёт зов и сообщение в бота. Примет — этот же план ляжет " +
                            "в его календарь, и дальше каждый правит свой.",
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )
                Spacer(Modifier.height(8.dp))
                list.forEach { handle ->
                    val on = handle in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (on) selected.remove(handle) else selected.add(handle)
                            }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MarkBox(on)
                        Spacer(Modifier.width(10.dp))
                        Text("@$handle", color = Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/**
 * Кнопка-таблетка. Растёт вместе со шрифтом, а не обрезает надпись.
 *
 * У ненажатой есть обводка. Без неё «ОТМЕНА» в окне отказа была белой
 * на белом: рядом с чёрной соседкой висела одна надпись без кнопки,
 * и понять, докуда она нажимается, было нельзя.
 *
 * Растяжение по высоте задаётся снаружи — тем, кто ставит её в ряд.
 * Внутри его быть не должно: вне ряда такая кнопка растянулась бы
 * на весь оставшийся экран.
 */
@Composable
private fun Pill(
    text: String,
    filled: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .heightIn(min = 42.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (!enabled) LightGray else if (filled) Black else White)
            .border(
                1.dp,
                if (filled || !enabled) Color.Transparent else LightGray,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (filled) White else Black,
            fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            textAlign = TextAlign.Center
        )
    }
}

/** «17 сентября, 19:30 · Грудь» — одной строкой. */
private fun planWhen(p: SyncApi.InvitePlan): String {
    val d = runCatching { LocalDate.ofEpochDay(p.date) }.getOrNull()
    val date = d?.format(DAY_MONTH) ?: ""
    val time = "%02d:%02d".format(p.hour, p.minute)
    return listOfNotNull(
        date.takeIf { it.isNotBlank() },
        time,
        p.muscleGroup.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
}
