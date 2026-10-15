package ru.styrk.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withStyle
import ru.styrk.app.data.model.ExerciseHistory
import ru.styrk.app.data.model.ExerciseNotes
import ru.styrk.app.data.model.MeasureType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import ru.styrk.app.data.model.ExerciseGuide
import ru.styrk.app.data.model.ExerciseMuscles
import ru.styrk.app.data.model.Muscle
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White

/**
 * Рекорды по упражнениям — для строки рекорда в заголовке карточки.
 *
 * Раздаётся один раз сверху (MainScreen), а не параметром: карточку
 * открывают четыре экрана, и у экрана выбора упражнений модели нет
 * вовсе — тащить её туда ради одной строки было бы хуже.
 */
val LocalExerciseHistory = compositionLocalOf<Map<String, ExerciseHistory>> { emptyMap() }

/** Рекорд одной строкой: «40 кг × 10», «25 повт.», «1:30», «5 км». */
fun recordLine(r: ExerciseHistory?): String? {
    if (r == null) return null
    return when (r.measureType) {
        MeasureType.TIME -> r.bestSeconds?.takeIf { it > 0 }?.let { SetFormat.time(it) }
        MeasureType.DISTANCE -> r.bestDistanceKm?.takeIf { it > 0f }?.let { SetFormat.num(it) + " км" }
        MeasureType.CARDIO -> null
        MeasureType.REPS -> r.maxReps.takeIf { it > 0 }?.let { "$it повт." }
        MeasureType.WEIGHT_REPS ->
            if (r.maxWeight > 0f) SetFormat.num(r.maxWeight) + " кг × " + maxOf(r.maxWeightReps, 1)
            else r.maxReps.takeIf { it > 0 }?.let { "$it повт." }
    }
}

/**
 * Карточка упражнения: ролик, группы и техника выполнения.
 *
 * Раньше это окно было написано дважды — на экране выбора упражнений
 * и на экране тренировки. Две копии успели разойтись: в одной техника
 * показывалась всегда, а в другой только вместе с роликом, из-за чего
 * у растяжки, где ролика нет по задумке, текст пропадал и вместо него
 * стояло «пока нет описания». Теперь окно одно на оба места.
 *
 * @param name название упражнения
 */
@Composable
fun ExerciseGuideDialog(
    name: String,
    /**
     * Описание и снимок, которые человек написал сам, — если это его
     * упражнение.
     *
     * Умолчаний здесь НЕТ нарочно. Забытое место вызова тогда молча
     * показывало бы «нет описания» при том, что описание есть, —
     * ровно то, на чём мы уже обожглись. Без умолчаний это ошибка
     * сборки, и каждое место вызова приходится решить осознанно.
     */
    ownDescription: String?,
    ownPhotoPath: String?,
    onDismiss: () -> Unit
) {
    val guide = ExerciseGuide.of(name)
    val muscles = ExerciseMuscles.of(name)
    val record = recordLine(LocalExerciseHistory.current[name])
    val ctx = LocalContext.current
    // version — чтобы карточка перечитала заметку после правки.
    val note = remember(name, ExerciseNotes.version) { ExerciseNotes.get(ctx, name) }
    var editingNote by remember(name) { mutableStateOf(false) }
    var noteDraft by remember(name) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        titleContentColor = Black,
        textContentColor = DarkGray,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = Black) } },
        // Рядом с названием — рекорд: с чем человек к этому
        // упражнению пришёл. Полужирным и золотом, как кубки.
        title = {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(name) }
                    if (record != null) {
                        append("  ")
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = Gold)) {
                            append(record)
                        }
                    }
                },
                fontSize = 15.sp
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {

                // Своё описание идёт ПЕРВЫМ, до готовой техники.
                //
                // Порядок не случаен: человек писал это про себя —
                // настройку тренажёра, свою поправку. Оно важнее общих
                // советов, которые он и так знает.
                if (ownPhotoPath != null) {
                    AsyncImage(
                        model = java.io.File(ownPhotoPath),
                        contentDescription = name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (!ownDescription.isNullOrBlank()) {
                    Text(ownDescription, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(14.dp))
                }

                if (guide != null && guide.hasGif) {
                    AsyncImage(
                        model = ExerciseGuide.assetUri(guide.gifAsset),
                        contentDescription = name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                }

                if (muscles.isNotEmpty()) {
                    MuscleTags(muscles)
                    Spacer(Modifier.height(14.dp))
                }

                if (guide != null && guide.technique.isNotEmpty()) {
                    Text("ТЕХНИКА", color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    guide.technique.forEach { tip ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text("•", color = Gold, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(tip, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                } else if (ownDescription.isNullOrBlank() && ownPhotoPath == null) {
                    // Пишем это, только когда сказать и правда нечего.
                    // Раньше здесь смотрели лишь на готовую технику —
                    // и своё упражнение с описанием получало «нет
                    // описания» при том, что описание было прямо рядом.
                    Text("Для этого упражнения пока нет описания", fontSize = 13.sp)
                }

                /* Своя заметка — под техникой, за линией. Кнопка —
                   карандашик: заметки нужны не всем, и крупная кнопка
                   мешала бы читать разбор. */
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = SoftGray)
                Spacer(Modifier.height(10.dp))
                if (!editingNote) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            note ?: "Моя заметка",
                            color = if (note != null) Black else DarkGray.copy(alpha = 0.6f),
                            fontSize = 13.sp, lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "✎", color = DarkGray, fontSize = 16.sp,
                            modifier = Modifier
                                .clickable { noteDraft = note ?: ""; editingNote = true }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = noteDraft,
                        onValueChange = { if (it.length <= 500) noteDraft = it },
                        placeholder = { Text("Например: сиденье на 4, спинка на 2",
                            color = DarkGray.copy(alpha = 0.6f), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Black, unfocusedBorderColor = SoftGray,
                            cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
                        )
                    )
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { editingNote = false }) {
                            Text("Отмена", color = DarkGray)
                        }
                        TextButton(onClick = {
                            ExerciseNotes.set(ctx, name, noteDraft)
                            editingNote = false
                        }) { Text("Сохранить", color = Black, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    )
}

/**
 * Свёрнутый блок «Как выполнять»: гифка, мышцы и техника (R184).
 *
 * Стоит внизу окна прогресса в «Рекордах». Свёрнут по умолчанию:
 * человек открыл рекорд ради графиков и чисел, а разбор техники нужен
 * не каждый раз — развёрнутый, он отодвигал бы главное вниз.
 *
 * Содержимое то же, что в карточке упражнения во время тренировки, —
 * своё описание и снимок первыми, потом гифка, мышцы и техника, — чтобы
 * одно и то же упражнение не выглядело в двух местах по-разному.
 * Если сказать нечего, блока нет вовсе: пустая шторка хуже никакой.
 */
@Composable
fun ExerciseHowToSection(
    name: String,
    ownDescription: String?,
    ownPhotoPath: String?
) {
    val guide = ExerciseGuide.of(name)
    val muscles = ExerciseMuscles.of(name)
    val hasGif = guide != null && guide.hasGif
    val hasTech = guide != null && guide.technique.isNotEmpty()
    if (!hasGif && !hasTech && muscles.isEmpty() &&
        ownDescription.isNullOrBlank() && ownPhotoPath == null) return

    var open by remember(name) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SoftGray)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { open = !open }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("КАК ВЫПОЛНЯТЬ", color = Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (open) "свернуть" else
                        listOfNotNull(
                            if (hasGif) "анимация" else null,
                            if (muscles.isNotEmpty()) "мышцы" else null,
                            if (hasTech || !ownDescription.isNullOrBlank()) "техника" else null
                        ).joinToString(", "),
                    color = DarkGray, fontSize = 11.sp, lineHeight = 15.sp
                )
            }
            Text(if (open) "⌃" else "⌄", color = DarkGray, fontSize = 16.sp)
        }
        if (open) {
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                if (ownPhotoPath != null) {
                    AsyncImage(
                        model = java.io.File(ownPhotoPath),
                        contentDescription = name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (!ownDescription.isNullOrBlank()) {
                    Text(ownDescription, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                }
                if (hasGif) {
                    // Белая подложка: гифки нарисованы на белом, и на сером
                    // фоне блока край картинки читался бы как рамка.
                    AsyncImage(
                        model = ExerciseGuide.assetUri(guide!!.gifAsset),
                        contentDescription = name,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(White)
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (muscles.isNotEmpty()) {
                    MuscleTags(muscles)
                    Spacer(Modifier.height(12.dp))
                }
                if (hasTech) {
                    guide!!.technique.forEach { tip ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("•", color = Gold, fontSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Text(tip, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Хештеги работающих мышц.
 *
 * Именно мышц, а не блоков экрана выбора: «Улица и дом» — это полка,
 * а не мышца, и отжимания грузят грудь с трицепсом независимо от того,
 * из какого блока их открыли.
 *
 * Первой идёт главная мышца, дальше помогающие — и выделена она
 * плотнее, чтобы с одного взгляда было видно, ради чего упражнение
 * вообще делается.
 *
 * Ряд прокручивается вбок: у базовых движений мышц бывает четыре,
 * а перенос на вторую строку в узком окне выглядит рвано.
 */
@Composable
private fun MuscleTags(muscles: List<Muscle>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        muscles.forEachIndexed { i, m ->
            val main = i == 0
            // Пробел в названии оставлен: «#Бицепсбедра» без него
            // читается тяжело, а внутри плашки хештег с пробелом
            // всё равно выглядит хештегом.
            Text(
                text = "#" + m.title,
                color = if (main) White else DarkGray,
                fontSize = 12.sp,
                fontWeight = if (main) FontWeight.SemiBold else FontWeight.Medium,
                modifier = Modifier
                    .background(if (main) Black else SoftGray, RoundedCornerShape(20.dp))
                    .padding(horizontal = 11.dp, vertical = 6.dp)
            )
        }
    }
}