package ru.styrk.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.SoftGray
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Общий сигнал «что-то изменилось, перечитайте себя».
 *
 * Окна тянут своё по отдельности и раньше делали это один раз
 * за открытие. Выходило так: человек дал согласие — а строка
 * «написать тренеру» появлялась только после выхода и повторного
 * входа. Он-то думает, что не сработало, и нажимает ещё раз.
 *
 * Теперь любое действие, меняющее связи, поднимает счётчик, а все
 * зависящие от него места на него подписаны. Одно место решает,
 * когда перечитывать, — и его нельзя забыть.
 */
object Refresh {
    var version by mutableStateOf(0)
        private set

    fun bump() {
        version++
        CoachStuff.forget()
    }
}

/**
 * Своё тренерское на стороне ученика: абонемент и заметки тренера.
 *
 * Абонемент завёл тренер, но платил за него ученик. Не показывать
 * человеку, сколько у него осталось занятий, — значит заставлять его
 * спрашивать голосом каждый раз. Показываем, но править не даём:
 * считает тот, кто проводит.
 *
 * Тянется один раз за запуск и кладётся сюда: и абонемент, и заметки
 * приходят одним ответом, а спрашивать сервер из каждого экрана
 * по отдельности — это три разных «пусто» вместо одного.
 */
object CoachStuff {
    private var loaded = false
    var subs by mutableStateOf<List<SyncApi.Sub>>(emptyList())
        private set
    var comments by mutableStateOf<List<SyncApi.CoachComment>>(emptyList())
        private set

    suspend fun loadOnce(api: SyncApi) {
        if (loaded) return
        loaded = true
        when (val r = api.myCoachStuff()) {
            is SyncApi.Result.Ok -> { subs = r.data.subs; comments = r.data.comments }
            is SyncApi.Result.Fail -> {
                // Молча: дневник работает без сервера, и красная строка
                // из-за пропавшей сети тут ничему не помогает.
                Unit
            }
        }
    }

    fun forget() { loaded = false; subs = emptyList(); comments = emptyList() }

    fun commentFor(uuid: String): SyncApi.CoachComment? =
        if (uuid.isBlank()) null else comments.firstOrNull { it.uuid == uuid }
}

private val DAY_FMT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru"))

/**
 * Заметка тренера к тренировке.
 *
 * Стоит рядом с самой тренировкой, а не отдельным списком где-то ещё:
 * человек читает «спину держи ровнее» и тут же видит, о какой спине
 * речь.
 */
@Composable
fun CoachCommentBlock(uuid: String) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    LaunchedEffect(Refresh.version) { if (prefs.isSignedIn) CoachStuff.loadOnce(api) }

    val c = CoachStuff.commentFor(uuid) ?: return
    Column(
        Modifier
            .fillMaxWidth()
            .background(SoftGray, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text("ЗАМЕТКА ТРЕНЕРА", color = DarkGray, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(Modifier.height(5.dp))
        Row {
            Text(c.from + ":", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            Text(c.text, color = Black, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/**
 * Абонементы — в профиле, рядом с аккаунтом.
 *
 * Не в календаре: календарь про то, когда заниматься, а абонемент про
 * то, сколько осталось, и смотрят их не в одну минуту.
 */
@Composable
fun SubsBlock() {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val signedIn = remember { prefs.isSignedIn }
    LaunchedEffect(signedIn, Refresh.version) { if (signedIn) CoachStuff.loadOnce(api) }

    val subs = CoachStuff.subs
    if (subs.isEmpty()) return

    val today = LocalDate.now().toEpochDay()

    Column(Modifier.fillMaxWidth()) {
        Text("АБОНЕМЕНТ", color = DarkGray, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(8.dp))
        subs.forEach { s ->
            val dead = (s.until != null && s.until < today) || s.left == 0
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(SoftGray, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(s.from, color = Black, fontSize = 14.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (dead) Text("кончился", color = Gold, fontSize = 11.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(
                            if (s.total != null) "занятий: ${s.left} из ${s.total}"
                            else "занятия не считаются"
                        )
                        s.until?.let {
                            append(" · до ")
                            append(runCatching { LocalDate.ofEpochDay(it).format(DAY_FMT) }
                                .getOrDefault(""))
                        }
                        if (s.note.isNotBlank()) { append(" · "); append(s.note) }
                    },
                    color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Считает тренер: занятие списывается, когда он его провёл.",
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}
