package ru.styrk.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Тренеры человека — те друзья, которым он открыл право «мой тренер».
 *
 * Отдельный запас, а не запрос из каждого места: по нему календарь
 * решает, показывать ли вкладку «Тренер», а сама вкладка — что на ней
 * рисовать. Два запроса дали бы два ответа, и вкладка могла бы
 * появиться пустой.
 */
object CoachFeed {
    var coaches by mutableStateOf<List<SyncApi.Friend>>(emptyList())
        internal set
}

/** Невидимый загрузчик тренеров. Ставится на экране календаря. */
@Composable
fun CoachWatch() {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val signedIn = remember { prefs.isSignedIn }
    LaunchedEffect(signedIn, Refresh.version) {
        if (!signedIn) { CoachFeed.coaches = emptyList(); return@LaunchedEffect }
        when (val r = api.friends()) {
            is SyncApi.Result.Ok -> CoachFeed.coaches =
                r.data.friends.filter { it.share.coach && it.coach != null }
            is SyncApi.Result.Fail -> Unit
        }
    }
}

private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))
private val DAY_W: DateTimeFormatter = DateTimeFormatter.ofPattern("EE, d MMMM", Locale.forLanguageTag("ru"))

/**
 * Вкладка «Тренер» в календаре.
 *
 * Всё, что связывает человека с тренером, — в одном месте, а не строкой
 * над календарём на любом выбранном дне. Сверху — кто тренер и что
 * он видит; дальше то, что требует ответа, то, что впереди, итог
 * назначенного, абонемент и заметки тренера к тренировкам.
 *
 * @param workoutDays дни, в которые были тренировки, — чтобы сказать,
 *   сколько назначенного сделано
 * @param onOpenDay открыть день в календаре — там отвечают на назначение
 */
@Composable
fun CoachTab(
    workoutDays: Set<LocalDate>,
    /** Что лежит в календаре: «день:час:минута». */
    plannedKeys: Set<String>,
    onOpenDay: (LocalDate) -> Unit,
    /** Положить принятое назначение в календарь, если его там нет. */
    onAdd: (SyncApi.InvitePlan) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    InviteWatch()
    LaunchedEffect(Refresh.version) { if (prefs.isSignedIn) CoachStuff.loadOnce(api) }

    val coaches = CoachFeed.coaches
    val today = LocalDate.now()
    // Все назначения тренера с ответами. Старый сервер этого списка
    // не знает — тогда берём хотя бы неотвеченные из входящих.
    val fromCoach = InviteFeed.data?.let { d ->
        d.coachPlans.ifEmpty { d.incoming }
    }.orEmpty().filter { it.fromCoach }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
    ) {
        Spacer(Modifier.height(14.dp))

        coaches.forEach { c ->
            CoachHeader(c)
            Spacer(Modifier.height(10.dp))
        }

        // «Когда мне удобно» — теперь здесь, а не над календарём.
        CoachWishRow()

        // ── Итог назначенного ──
        // Выполненным считается и сегодняшнее, если сегодня уже была
        // тренировка: иначе оно так и висело бы во «Впереди».
        fun doneToday(i: SyncApi.Invite): Boolean {
            val d = LocalDate.ofEpochDay(i.plan.date)
            return d == today && d in workoutDays
        }
        val past = fromCoach.filter {
            it.status == "accepted" && (LocalDate.ofEpochDay(it.plan.date) < today || doneToday(it))
        }
        val done = past.count { LocalDate.ofEpochDay(it.plan.date) in workoutDays }
        val pending = fromCoach.filter { it.status == "pending" }
            .sortedBy { it.plan.date }
        if (fromCoach.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(16.dp))
                    .padding(vertical = 14.dp)
            ) {
                Stat(fromCoach.size.toString(), "назначено", Modifier.weight(1f))
                Stat(if (past.isEmpty()) "—" else "$done из ${past.size}", "сделано\nза 2 месяца", Modifier.weight(1f))
                Stat(pending.size.toString(), "ждут ответа", Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
        }

        // ── Ждут ответа ──
        if (pending.isNotEmpty()) {
            Label("ЖДУТ ОТВЕТА")
            pending.forEach { inv ->
                val d = LocalDate.ofEpochDay(inv.plan.date)
                PlanRow(inv, d, gold = true) { onOpenDay(d) }
                Spacer(Modifier.height(8.dp))
            }
            Text("Нажмите — откроется день в календаре, там «Приду» или «Не смогу».",
                color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp)
            Spacer(Modifier.height(14.dp))
        }

        // ── Впереди ──
        val ahead = fromCoach.filter {
            it.status == "accepted" && LocalDate.ofEpochDay(it.plan.date) >= today && !doneToday(it)
        }
            .sortedBy { it.plan.date * 10000 + it.plan.hour * 60 + it.plan.minute }
        if (ahead.isNotEmpty()) {
            Label("ВПЕРЕДИ")
            ahead.take(8).forEach { inv ->
                val d = LocalDate.ofEpochDay(inv.plan.date)
                val inCalendar = "${inv.plan.date}:${inv.plan.hour}:${inv.plan.minute}" in plannedKeys
                PlanRow(inv, d, gold = false) { onOpenDay(d) }
                /* Приняли, а в календаре его нет — например, ответили
                   с другого устройства, а обмен ещё не прошёл. Не прятать,
                   а предложить положить одним нажатием. */
                if (!inCalendar) {
                    Row(Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("В календаре этой тренировки нет", color = DarkGray,
                            fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text("ДОБАВИТЬ", color = Black, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp))
                                .clickable { onAdd(inv.plan); onOpenDay(d) }
                                .padding(horizontal = 8.dp, vertical = 6.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
        }

        // ── Абонемент ──
        SubsBlock()

        // ── Заметки тренера ──
        val notes = CoachStuff.comments.sortedByDescending { it.at }.take(10)
        if (notes.isNotEmpty()) {
            Label("ЗАМЕТКИ ТРЕНЕРА")
            notes.forEach { n ->
                Column(
                    Modifier.fillMaxWidth().background(SoftGray, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        n.from + (if (n.at > 0) " · " + java.time.Instant.ofEpochMilli(n.at)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DAY) else ""),
                        color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(n.text, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (fromCoach.isEmpty() && CoachStuff.comments.isEmpty() && CoachStuff.subs.isEmpty()) {
            Text(
                "Тренер пока ничего не назначал. Когда назначит — тренировки появятся " +
                        "здесь и в календаре, а вы ответите «Приду» или «Не смогу».",
                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

/** Чёрная карточка: кто тренер и что ему открыто. */
@Composable
private fun CoachHeader(c: SyncApi.Friend) {
    val card = c.coach ?: return
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Black)
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("ВАШ ТРЕНЕР", color = Gold, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(6.dp))
        Text(card.name, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(
            listOf(card.gym, "@" + c.handle).filter { it.isNotBlank() }.joinToString(" · "),
            color = White.copy(alpha = .6f), fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        val sees = buildList {
            add("дневник")
            if (c.share.body) add("замеры тела")
        }.joinToString(" и ")
        val until = c.share.until?.let {
            "до " + runCatching { LocalDate.ofEpochDay(it).format(DAY) }.getOrDefault("")
        } ?: "бессрочно"
        Text("Видит $sees · доступ $until", color = White.copy(alpha = .8f),
            fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Gold, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text("Видит тренировку вживую, пока она идёт, и может вписать подход за вас",
                color = White.copy(alpha = .6f), fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text("Изменить доступ — Профиль → Друзья", color = White.copy(alpha = .4f), fontSize = 10.sp)
    }
}

@Composable
private fun PlanRow(inv: SyncApi.Invite, d: LocalDate, gold: Boolean, onClick: () -> Unit) {
    val p = inv.plan
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(SoftGray)
            .clickable { onClick() }.padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                d.format(DAY_W).replaceFirstChar { it.uppercase() } +
                        " · %02d:%02d".format(p.hour, p.minute),
                color = if (gold) Gold else Black, fontSize = 13.sp, fontWeight = FontWeight.Bold
            )
            Text(
                listOfNotNull(
                    p.muscleGroup.takeIf { it.isNotBlank() },
                    p.exercises.size.takeIf { it > 0 }?.let { "$it упр." },
                    inv.planNote.takeIf { it.isNotBlank() }
                ).joinToString(" · ").ifEmpty { "Тренировка" },
                color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp
            )
        }
        Text("›", color = DarkGray, fontSize = 18.sp)
    }
}

@Composable
private fun Label(t: String) {
    Text(t, color = DarkGray, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Stat(v: String, l: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, color = Black, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(l, color = DarkGray, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}
