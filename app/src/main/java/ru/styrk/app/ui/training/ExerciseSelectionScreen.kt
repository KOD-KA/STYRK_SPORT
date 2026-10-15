package ru.styrk.app.ui.training

import ru.styrk.app.ui.common.keyboardGap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import coil3.compose.AsyncImage
import ru.styrk.app.data.model.ExerciseGuide
import ru.styrk.app.ui.common.ActionRow
import ru.styrk.app.ui.common.ExerciseGuideDialog
import ru.styrk.app.ui.common.RowAction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.data.model.ExerciseCatalog
import ru.styrk.app.data.model.ExerciseLibrary
import ru.styrk.app.data.model.MeasureType
import ru.styrk.app.data.model.MuscleGroup
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.Gold
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import ru.styrk.app.data.model.CustomExercise
import ru.styrk.app.ui.common.ExercisePhoto

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ExerciseSelectionScreen(
    muscleGroup: MuscleGroup,
    alreadyAdded: List<String> = emptyList(),
    customExercises: List<String> = emptyList(),
    lastGroupExercises: List<String> = emptyList(),
    onBack: () -> Unit,
    onRepeatLastGroup: () -> Unit,
    onAddCustomExercise: (String, MeasureType) -> Unit,
    onRenameCustom: (String, String) -> Unit = { _, _ -> },
    // Свои упражнения целиком: нужны описание и снимок, а по одному
    // названию их не найти.
    //
    // ЗНАЧЕНИЙ ПО УМОЛЧАНИЮ ЗДЕСЬ НЕТ, И ЭТО НАРОЧНО.
    //
    // Сначала они были — «чтобы вызывающий код не пришлось трогать».
    // Обернулось это тем, что забытый файл не ломал сборку: экран
    // подставлял пустой список и пустое действие, окно открывалось,
    // текст набирался, «Сохранить» нажималось — и не сохранялось
    // ничего. Молча.
    //
    // Без умолчаний та же недостача становится ошибкой сборки: её
    // видно сразу и нельзя не заметить.
    customDetails: List<CustomExercise>,
    onSaveCustomDetails: (String, String?, String?) -> Unit,
    // Удалить своё упражнение из списка. История подходов остаётся.
    onDeleteCustom: (String) -> Unit = {},
    // Сколько тренировок было с каждым упражнением — частые идут наверх.
    usage: Map<String, Int> = emptyMap(),
    onExerciseSelected: (String) -> Unit
) {
    // п8: имя своего упражнения, которое сейчас переименовывают
    var renamingName by remember { mutableStateOf<String?>(null) }
    // п5: имя упражнения, для которого открыта увеличенная гифка
    var gifDialogName by remember { mutableStateOf<String?>(null) }
    // v1.7.1 (п5): поиск по упражнениям внутри группы
    var searchQuery by remember(muscleGroup) { mutableStateOf("") }
    /* Самые частые — сверху. Со временем список сам подстраивается
       под человека: то, что он делает каждую неделю, не надо искать
       среди тридцати строк. Сортировка устойчивая: у ни разу не сделанных
       остаётся прежний порядок справочника. */
    val allLibrary = ExerciseLibrary.exercises.filter { it.muscleGroup == muscleGroup }
        .sortedByDescending { usage[it.name] ?: 0 }
    val q = searchQuery.trim().lowercase()
    val libraryExercises = if (q.isBlank()) allLibrary
    else allLibrary.filter { it.name.lowercase().contains(q) }
    val sortedCustom = customExercises.sortedByDescending { usage[it] ?: 0 }
    val shownCustom = if (q.isBlank()) sortedCustom
    else sortedCustom.filter { it.lowercase().contains(q) }

    /* Найденное В ДРУГИХ блоках.

       Человек ищет «планка», стоя в блоке «Пресс», — и не находит,
       потому что планка лежит в «Улице и доме». Дальше он либо
       заводит дубль своим упражнением, либо уходит назад перебирать
       блоки руками. И то и другое — работа, которой быть не должно:
       он набрал название, название существует, показать его мы можем.

       Показываем ПОСЛЕ своего блока и отдельно, с чертой и подписью:
       порядок «сначала то, за чем пришёл» не ломается, а откуда взялось
       упражнение — видно сразу. Пустой поиск ничего этого не трогает:
       там человек листает свой блок, и чужие ему ни к чему.

       Взять отсюда упражнение можно — оно ляжет в тот блок, где вы
       сейчас. Так и задумано: «сегодня грудь, но добавлю ещё планку». */
    val elsewhere: List<Pair<MuscleGroup, List<ExerciseCatalog>>> =
        if (q.isBlank()) emptyList()
        else MuscleGroup.visible
            .filter { !it.sameAs(muscleGroup) }
            .mapNotNull { g ->
                val found = ExerciseLibrary.exercises.filter {
                    it.muscleGroup == g && it.name.lowercase().contains(q)
                }
                if (found.isEmpty()) null else g to found
            }
    var addingCustom by remember(muscleGroup) { mutableStateOf(false) }
    var customText by remember(muscleGroup) { mutableStateOf("") }
    // «Как считать» — для растяжки по умолчанию время, иначе вес×повторы
    var customType by remember(muscleGroup) {
        mutableStateOf(if (muscleGroup == MuscleGroup.STRETCH) MeasureType.TIME else MeasureType.WEIGHT_REPS)
    }
    var typeMenuOpen by remember(muscleGroup) { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {

        // ===== ШАПКА С КНОПКОЙ НАЗАД =====
        Column(
            modifier = Modifier.fillMaxWidth().background(Black).padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(40.dp).clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("←", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                Column {
                    Text("ВЫБЕРИТЕ УПРАЖНЕНИЯ", color = White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    Text(muscleGroup.title, color = White.copy(alpha = 0.6f), fontSize = 13.sp)
                }
            }
        }

        // ===== СПИСОК УПРАЖНЕНИЙ =====
        LazyColumn(
            /* imePadding ВЕРНУЛСЯ — и вот почему.

               Когда-то его убрали: окно ужималось само (adjustResize
               в манифесте), и отступ накладывался вторым слоем — над
               клавиатурой висела белая полоса.

               С тех пор приложение стало рисоваться во весь экран
               (enableEdgeToEdge в MainActivity), а при этом «adjustResize»
               перестаёт действовать: система больше не ужимает окно,
               а присылает высоту клавиатуры отступом — и применить его
               должны мы сами. Второму слою взяться неоткуда, зато без
               первого список считает, что видно его целиком, и поле
               ввода уезжает под клавиатуру. bringIntoView ниже при этом
               честно доводит поле до низа окна — вот только низ окна
               он считает по той же ошибочной высоте. */
            modifier = Modifier.fillMaxSize().keyboardGap(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ── ПОИСК (п5) ─────────────────────────────────────────────
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Поиск упражнения…", color = DarkGray, fontSize = 14.sp) },
                    leadingIcon = { Text("🔍", fontSize = 15.sp) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            Text(
                                "✕", color = DarkGray, fontSize = 15.sp,
                                modifier = Modifier
                                    .clickable { searchQuery = "" }
                                    .padding(10.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Black, unfocusedBorderColor = DarkGray.copy(alpha = 0.3f),
                        cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
                    )
                )
            }

            // Ничего не найдено
            if (q.isNotBlank() && libraryExercises.isEmpty() && shownCustom.isEmpty()
                && elsewhere.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Ничего не найдено", color = DarkGray, fontSize = 13.sp)
                    }
                }
            }

            // ── Повторить как в прошлый раз ────────────────────────────
            if (lastGroupExercises.isNotEmpty() && q.isBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SoftGray, RoundedCornerShape(14.dp))
                            .clickable { onRepeatLastGroup() }
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("↻", color = Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Повторить как в прошлый раз",
                                color = Black,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = lastGroupExercises.joinToString(", "),
                            fontSize = 12.sp,
                            color = DarkGray
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Black, RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                "Добавить все (${lastGroupExercises.size}) →",
                                color = White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(2.dp)) }
            }

            items(libraryExercises) { exercise ->
                val isAdded = exercise.name in alreadyAdded
                ExerciseListItem(
                    name = exercise.name, isAdded = isAdded, isCustom = false,
                    onOpenGif = { gifDialogName = exercise.name }
                ) {
                    onExerciseSelected(exercise.name)
                }
            }

            // ── Свои упражнения ──────────────────────────────────────
            if (shownCustom.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "СВОИ УПРАЖНЕНИЯ",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkGray,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(shownCustom) { name ->
                    val isAdded = name in alreadyAdded
                    ExerciseListItem(
                        name = name, isAdded = isAdded, isCustom = true,
                        onRename = { renamingName = name },
                        onOpenGif = { gifDialogName = name }
                    ) {
                        onExerciseSelected(name)
                    }
                }
            }

            // ── Найденное в других блоках ────────────────────────────
            elsewhere.forEach { (group, found) ->
                item {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(
                            Modifier.weight(1f), thickness = 1.dp,
                            color = DarkGray.copy(alpha = 0.18f)
                        )
                        Text(
                            group.title.uppercase(),
                            modifier = Modifier.padding(horizontal = 10.dp),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = DarkGray, letterSpacing = 1.2.sp
                        )
                        HorizontalDivider(
                            Modifier.weight(1f), thickness = 1.dp,
                            color = DarkGray.copy(alpha = 0.18f)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
                items(found) { exercise ->
                    val isAdded = exercise.name in alreadyAdded
                    ExerciseListItem(
                        name = exercise.name, isAdded = isAdded, isCustom = false,
                        onOpenGif = { gifDialogName = exercise.name }
                    ) {
                        onExerciseSelected(exercise.name)
                    }
                }
            }

            // ── Переименование своего упражнения (п8) ────────────────
            // (диалог рендерится ниже, вне списка)

            // ── Добавить своё упражнение ─────────────────────────────
            item {
                if (!addingCustom) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Black, RoundedCornerShape(12.dp))
                            .clickable { addingCustom = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("+", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(10.dp))
                        Text("Добавить своё упражнение", color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                } else {
                    val bringIntoView = remember { BringIntoViewRequester() }
                    val scope = rememberCoroutineScope()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SoftGray, RoundedCornerShape(12.dp))
                            .padding(14.dp)
                            .bringIntoViewRequester(bringIntoView)
                    ) {
                        Text("Название упражнения", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customText,
                                onValueChange = { customText = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusEvent { st ->
                                        if (st.isFocused) scope.launch { bringIntoView.bringIntoView() }
                                    },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                    onDone = {
                                        if (customText.isNotBlank()) {
                                            onAddCustomExercise(customText.trim(), customType)
                                            customText = ""
                                            addingCustom = false
                                        }
                                    }
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Black, unfocusedBorderColor = DarkGray.copy(alpha = 0.3f),
                                    cursorColor = Black, focusedTextColor = Black, unfocusedTextColor = Black
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (customText.isNotBlank()) Black else DarkGray.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = customText.isNotBlank()) {
                                        onAddCustomExercise(customText.trim(), customType)
                                        customText = ""
                                        addingCustom = false
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✓", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(10.dp))

                        // ── КАК СЧИТАТЬ (выпадающее меню) ────────────
                        Text("Как считать", fontSize = 12.sp, color = DarkGray, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(6.dp))
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(White, RoundedCornerShape(10.dp))
                                    .clickable { typeMenuOpen = true }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(customType.title, color = Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                    Text(customType.shortHint, color = DarkGray, fontSize = 11.sp)
                                }
                                Text("▾", color = DarkGray, fontSize = 14.sp)
                            }
                            DropdownMenu(
                                expanded = typeMenuOpen,
                                onDismissRequest = { typeMenuOpen = false },
                                containerColor = White
                            ) {
                                MeasureType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(type.title, color = Black, fontSize = 14.sp,
                                                    fontWeight = if (type == customType) FontWeight.Bold else FontWeight.Normal)
                                                Text(type.shortHint, color = DarkGray, fontSize = 11.sp)
                                            }
                                        },
                                        onClick = {
                                            customType = type
                                            typeMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Отмена", fontSize = 12.sp, color = DarkGray,
                            modifier = Modifier.clickable { addingCustom = false; customText = "" }
                        )
                    }
                }
            }
        }
    }

    // ── ОКНО СВОЕГО УПРАЖНЕНИЯ: название, описание, снимок ──
    //
    // Раньше здесь переименовывали, и только. Описание и снимок легли
    // сюда же, а не в отдельное окно: это всё про одно упражнение,
    // и открывается оно одним и тем же долгим нажатием. Второе окно
    // означало бы второй путь к тому же самому.
    renamingName?.let { oldName ->
        val current = customDetails.find { it.name == oldName }
        var newName by remember(oldName) { mutableStateOf(oldName) }
        var descText by remember(oldName) { mutableStateOf(current?.description.orEmpty()) }
        var photo by remember(oldName) { mutableStateOf(current?.photoPath) }
        var askDelete by remember(oldName) { mutableStateOf(false) }
        val ctx = LocalContext.current

        val pickPhoto = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            if (uri != null) {
                val saved = ExercisePhoto.save(ctx, uri)
                if (saved != null) photo = saved
                else Toast.makeText(ctx, "Не удалось прочитать снимок", Toast.LENGTH_SHORT).show()
            }
        }

        AlertDialog(
            onDismissRequest = { renamingName = null },
            containerColor = White,
            title = { Text("Своё упражнение", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("Название") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = descText, onValueChange = { descText = it },
                        label = { Text("Описание — необязательно") },
                        // Несколько строк: сюда пишут настройку тренажёра
                        // или на что смотреть, а это редко умещается в одну.
                        minLines = 2, maxLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    val shown = photo
                    if (shown != null) {
                        AsyncImage(
                            model = java.io.File(shown),
                            contentDescription = "Снимок упражнения",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Spacer(Modifier.height(6.dp))
                        ActionRow {
                            RowAction("Заменить", strong = true) {
                                pickPhoto.launch(arrayOf("image/*"))
                            }
                            RowAction("Убрать") { photo = null }
                        }
                    } else {
                        RowAction("Добавить снимок", strong = true) {
                            pickPhoto.launch(arrayOf("image/*"))
                        }
                    }

                    /* Удаление — каждого упражнения по отдельности,
                       с переспросом. Из списка оно уходит, а сделанные
                       подходы остаются в архиве и рекордах: удалить
                       упражнение не значит переписать прошлое. */
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = DarkGray.copy(alpha = 0.18f))
                    Spacer(Modifier.height(6.dp))
                    if (!askDelete) {
                        RowAction("Удалить упражнение") { askDelete = true }
                    } else {
                        Text(
                            "Упражнение пропадёт из списка. Подходы, которые вы уже " +
                                    "сделали, останутся в архиве и рекордах.",
                            fontSize = 12.sp, color = DarkGray, lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        ActionRow {
                            RowAction("Отмена") { askDelete = false }
                            RowAction("Удалить", strong = true) {
                                onDeleteCustom(oldName)
                                renamingName = null
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    // Сохраняем по НАЗВАНИЮ, а не по номеру записи.
                    //
                    // Номер приходится брать из списка, а список может
                    // не доехать — и тогда сохранять оказывается нечего.
                    // Название же есть всегда: по нему это окно и открыли.
                    // Найти по нему запись — забота того, у кого список
                    // точно есть.
                    //
                    // Порядок важен: сначала описание и снимок под старым
                    // названием, потом переименование. Наоборот — и искать
                    // пришлось бы уже несуществующее имя.
                    onSaveCustomDetails(oldName, descText, photo)
                    if (newName.trim() != oldName) onRenameCustom(oldName, newName)
                    renamingName = null
                }) { Text("Сохранить", color = Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { renamingName = null }) {
                    Text("Отмена", color = DarkGray)
                }
            }
        )
    }

    // ── КАРТОЧКА УПРАЖНЕНИЯ: РОЛИК, ГРУППЫ, ТЕХНИКА ──
    // Раньше это окно было расписано прямо здесь и второй раз на экране
    // тренировки. Копии успели разойтись — теперь окно общее.
    gifDialogName?.let { gname ->
        val own = customDetails.find { it.name == gname }
        ExerciseGuideDialog(
            name = gname,
            ownDescription = own?.description,
            ownPhotoPath = own?.photoPath,
            onDismiss = { gifDialogName = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExerciseListItem(
    name: String,
    isAdded: Boolean,
    isCustom: Boolean,
    onRename: (() -> Unit)? = null,
    onOpenGif: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = if (isAdded) SoftGray else White, shape = RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(text = name, color = Black, fontSize = 15.sp, fontWeight = if (isAdded) FontWeight.Medium else FontWeight.Normal)
            if (isCustom) {
                Spacer(Modifier.width(6.dp))
                Text("★", color = Gold, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.width(8.dp))

        // ── Поле показа: ролик или затемнённая заглушка ──
        // Раньше при отсутствии ролика здесь не рисовалось ничего.
        // У блока «Растяжка», где роликов нет по задумке, это значило,
        // что карточку с разбором открыть просто не за что: нажимать
        // было не на что. Плюс строки списка выходили разной длины.
        // Теперь поле есть всегда, и оно всегда открывает карточку.
        val guide = ExerciseGuide.of(name)
        if (onOpenGif != null) {
            val slot = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(onClick = { onOpenGif() }, onLongClick = { onOpenGif() })

            if (guide != null && guide.hasGif) {
                AsyncImage(
                    model = ExerciseGuide.assetUri(guide.gifAsset),
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = slot
                )
            } else {
                Box(
                    modifier = slot.background(SoftGray),
                    contentAlignment = Alignment.Center
                ) {
                    // Значок разбора, а не пустой серый прямоугольник:
                    // пустота читается как «картинка не загрузилась».
                    Text("i", color = DarkGray, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(8.dp))
        }

        // п8: карандаш для переименования своего упражнения
        if (onRename != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(SoftGray, RoundedCornerShape(8.dp))
                    .clickable { onRename() },
                contentAlignment = Alignment.Center
            ) { Text("✎", color = DarkGray, fontSize = 14.sp) }
            Spacer(Modifier.width(8.dp))
        }

        if (isAdded) {
            Box(
                modifier = Modifier.size(28.dp).background(Black, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        } else {
            Box(
                modifier = Modifier.size(28.dp).background(SoftGray, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("+", color = DarkGray, fontSize = 18.sp, fontWeight = FontWeight.Medium) }
        }
    }
}