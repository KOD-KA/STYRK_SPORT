package ru.styrk.app.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.styrk.app.data.model.ExerciseGuide
import ru.styrk.app.data.model.ExerciseMuscles
import ru.styrk.app.data.model.Legend
import ru.styrk.app.data.model.ProgramDay
import ru.styrk.app.data.model.TrainingProgram
import ru.styrk.app.ui.common.ExerciseGuideDialog
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White

/*
 * «ПОДРОБНЕЕ» О ГОТОВОЙ ТРЕНИРОВКЕ
 *
 * Как в календаре: тёмная шапка, строка чисел, дальше содержимое и кнопки.
 * Внутри — как выполнять эту тренировку и все упражнения: у каждого
 * ролик-миниатюра и мышцы, которые оно грузит. Нажатие на упражнение
 * открывает ту же карточку разбора, что и везде: ролик целиком и технику.
 *
 * У легенд — свой, «легендарный» стиль: чёрный фон, золото и цитата.
 * Фотографий нет нарочно: снимки чемпионов принадлежат фотографам,
 * и класть их в приложение без разрешения нельзя.
 */

/** Цвета листа: обычный — светлый, у легенды — чёрный с золотом. */
private class Palette(val dark: Boolean) {
    val bg = if (dark) Black else White
    val head = if (dark) Black else DarkGray
    val band = if (dark) White.copy(alpha = .06f) else SoftGray
    val text = if (dark) White else Black
    val muted = if (dark) White.copy(alpha = .62f) else DarkGray
    val line = if (dark) White.copy(alpha = .12f) else SoftGray
    val accent = Gold
}

/** Подробно о подборке или о легенде — один лист на оба случая. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutDetailContent(
    title: String,
    subtitle: String,
    stats: List<Pair<String, String>>,
    guide: List<String>,
    exercises: List<String>,
    onStart: () -> Unit,
    onPlan: () -> Unit,
    legend: Legend? = null
) {
    val c = remember(legend != null) { Palette(legend != null) }
    var guideName by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth().background(c.bg),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item { DetailHead(c, title, subtitle, legend?.emoji) }

        val quote = legend?.quote
        if (legend != null && quote != null) {
            item { QuoteBlock(quote, legend.name, legend.quoteSource) }
        }

        item {
            FlowRow(
                Modifier.fillMaxWidth().background(c.band)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                stats.forEachIndexed { i, (v, l) ->
                    if (i > 0) Box(Modifier.width(1.dp).height(28.dp).background(c.line))
                    Column(Modifier.padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text,
                            maxLines = 1, softWrap = false)
                        Text(l, fontSize = 10.sp, color = c.muted, maxLines = 1, softWrap = false)
                    }
                }
            }
        }

        if (legend != null) {
            item {
                Text(legend.description, color = c.text.copy(alpha = .88f), fontSize = 13.sp,
                    lineHeight = 19.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))
            }
            item { Label(c, "КАК ТРЕНИРОВАЛСЯ") }
            items(legend.workout.size) { i -> Bullet(c, legend.workout[i]) }
        }

        if (guide.isNotEmpty()) {
            item { Label(c, "КАК ВЫПОЛНЯТЬ") }
            items(guide.size) { i -> Bullet(c, guide[i]) }
        }

        item { Label(c, "УПРАЖНЕНИЯ · " + exercises.size) }
        itemsIndexed(exercises) { i, ex ->
            ExerciseLine(c, i + 1, ex) { guideName = ex }
            if (i < exercises.lastIndex) {
                HorizontalDivider(color = c.line, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .background(if (c.dark) Gold else Black, RoundedCornerShape(14.dp))
                        .clickable { onStart() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(if (legend != null) "ПОПРОБОВАТЬ" else "НАЧАТЬ ЭТУ ТРЕНИРОВКУ",
                        color = if (c.dark) Black else White,
                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .background(c.band, RoundedCornerShape(14.dp))
                        .clickable { onPlan() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Запланировать", color = c.muted, fontSize = 13.sp)
                }
            }
        }
    }

    guideName?.let { n ->
        // Здесь только упражнения справочника: своих описаний и снимков у них нет.
        ExerciseGuideDialog(name = n, ownDescription = null, ownPhotoPath = null,
            onDismiss = { guideName = null })
    }
}

/** Подробно о программе: как её проходить и все дни с упражнениями. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramDetailContent(
    program: TrainingProgram,
    onPlanDay: (ProgramDay) -> Unit
) {
    val c = remember { Palette(false) }
    var guideName by remember { mutableStateOf<String?>(null) }
    val total = program.days.sumOf { it.exercises.size }

    LazyColumn(Modifier.fillMaxWidth().background(c.bg),
        contentPadding = PaddingValues(bottom = 32.dp)) {

        item { DetailHead(c, program.title, program.subtitle, null) }

        item {
            FlowRow(
                Modifier.fillMaxWidth().background(c.band)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                listOf("${program.days.size}" to "дней в неделю", "$total" to "упражнений")
                    .forEachIndexed { i, (v, l) ->
                        if (i > 0) Box(Modifier.width(1.dp).height(28.dp).background(c.line))
                        Column(Modifier.padding(horizontal = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text)
                            Text(l, fontSize = 10.sp, color = c.muted)
                        }
                    }
            }
        }

        if (program.guide.isNotEmpty()) {
            item { Label(c, "КАК ПРОХОДИТЬ") }
            items(program.guide.size) { i -> Bullet(c, program.guide[i]) }
        }

        program.days.forEach { day ->
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(day.label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
                        Text(day.muscleGroup, fontSize = 12.sp, color = c.muted)
                    }
                    // «+» — запланировать этот день, как в списке программ
                    Box(
                        Modifier.size(34.dp).background(Black, CircleShape).clickable { onPlanDay(day) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            itemsIndexed(day.exercises) { i, ex ->
                ExerciseLine(c, i + 1, ex) { guideName = ex }
                if (i < day.exercises.lastIndex) {
                    HorizontalDivider(color = c.line, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
        }

        item {
            Text("Нажмите «+» у дня, чтобы поставить его в календарь. Нажатие на упражнение " +
                    "открывает ролик и технику.",
                color = c.muted, fontSize = 12.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))
        }
    }

    guideName?.let { n ->
        ExerciseGuideDialog(name = n, ownDescription = null, ownPhotoPath = null,
            onDismiss = { guideName = null })
    }
}

// ── части листа ──────────────────────────────────────────────────────

@Composable
private fun DetailHead(c: Palette, title: String, subtitle: String, emoji: String?) {
    Column(Modifier.fillMaxWidth().background(c.head)
        .padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (emoji != null) {
                Text(emoji, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
            }
            Text(title, color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(3.dp))
        Text(subtitle, color = if (c.dark) Gold else White.copy(alpha = .7f), fontSize = 13.sp)
    }
}

/** Цитата легенды: крупно, золотой кавычкой, с подписью. */
@Composable
private fun QuoteBlock(quote: String, author: String, source: String?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 18.dp)
            .background(White.copy(alpha = .05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text("«", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
        Text(quote, color = White, fontSize = 16.sp, fontStyle = FontStyle.Italic, lineHeight = 23.sp)
        Spacer(Modifier.height(10.dp))
        Text("— " + author + (source?.let { ", $it" } ?: ""), color = Gold, fontSize = 12.sp)
    }
}

@Composable
private fun Label(c: Palette, text: String) {
    Text(text, color = if (c.dark) Gold else DarkGray, fontSize = 12.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
private fun Bullet(c: Palette, text: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(5.dp).background(Gold, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(text, color = c.text.copy(alpha = .9f), fontSize = 13.sp, lineHeight = 19.sp)
    }
}

/**
 * Строка упражнения: номер, название, какие мышцы работают, ролик справа.
 * Мышцы — это и есть «зачем это упражнение здесь»: первой идёт главная.
 */
@Composable
private fun ExerciseLine(c: Palette, n: Int, name: String, onOpen: () -> Unit) {
    val muscles = remember(name) { ExerciseMuscles.of(name).joinToString(" · ") { it.title } }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$n", color = c.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(22.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = c.text, fontSize = 14.sp)
            if (muscles.isNotEmpty()) {
                Text(muscles, color = c.muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
        Spacer(Modifier.width(10.dp))
        ExerciseThumb(name, c.dark)
    }
}

/**
 * Миниатюра ролика. У растяжки ролика нет по задумке — тогда «i»,
 * чтобы строки были одной длины и карточку было за что открыть.
 * На чёрном листе ролик лежит на белой подложке: рисунки сделаны
 * под светлый фон.
 */
@Composable
private fun ExerciseThumb(name: String, dark: Boolean) {
    val guide = ExerciseGuide.of(name)
    val slot = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
    if (guide != null && guide.hasGif) {
        AsyncImage(
            model = ExerciseGuide.assetUri(guide.gifAsset),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = slot.background(White)
        )
    } else {
        Box(slot.background(if (dark) White.copy(alpha = .1f) else SoftGray),
            contentAlignment = Alignment.Center) {
            Text("i", color = if (dark) White.copy(alpha = .6f) else DarkGray,
                fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}
