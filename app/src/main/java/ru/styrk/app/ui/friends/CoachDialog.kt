package ru.styrk.app.ui.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.common.ActionRow
import ru.styrk.app.ui.common.RowAction
import ru.styrk.app.ui.common.WideButton
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.LightGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White

/**
 * «Стать тренером».
 *
 * Тренером становится всякий, кто заполнит имя и зал. Проверки
 * документов здесь нет и быть не может: мы не орган аттестации.
 *
 * Защита не в том, чтобы отсеять самозванцев на входе, а в том, что
 * звание само по себе не даёт НИЧЕГО. Пока ученик не откроет доступ
 * отдельным решением, тренер видит ровно то же, что любой друг, —
 * то есть ничего.
 *
 * Окно живёт в разделе «О приложении», а не в профиле, и это не
 * случайность: профиль — про себя и своё тело, туда заходят каждый
 * день. Тренером человек становится один раз.
 */
@Composable
fun CoachDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val scope = rememberCoroutineScope()
    val signedIn = remember { prefs.isSignedIn }

    var card by remember { mutableStateOf<SyncApi.CoachCard?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var gym by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var quitting by remember { mutableStateOf(false) }
    var students by remember { mutableStateOf<List<SyncApi.Student>?>(null) }

    LaunchedEffect(signedIn) {
        if (!signedIn) { loaded = true; return@LaunchedEffect }
        when (val r = api.me()) {
            is SyncApi.Result.Ok -> {
                card = r.data.coach
                name = r.data.coach?.name.orEmpty()
                gym = r.data.coach?.gym.orEmpty()
            }
            is SyncApi.Result.Fail -> message = r.message
        }
        loaded = true
        if (card != null) {
            when (val s = api.students()) {
                is SyncApi.Result.Ok -> students = s.data
                is SyncApi.Result.Fail -> Unit
            }
        }
    }

    val maxBody = (LocalConfiguration.current.screenHeightDp * 0.8).dp

    Dialog(onDismissRequest = { if (!busy) onDismiss() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(White)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Black)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    if (card != null) "ВЫ ТРЕНЕР" else "СТАТЬ ТРЕНЕРОМ",
                    color = White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Планировать тренировки за своих учеников — тех, кто сам " +
                            "откроет вам такое право.",
                    color = White.copy(.62f), fontSize = 12.sp, lineHeight = 17.sp
                )
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxBody)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                if (!signedIn) {
                    Text(
                        "Тренерство живёт на сервере, как и друзья: без аккаунта " +
                                "ученикам нечего вам открывать.",
                        color = Black, fontSize = 13.sp, lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Войдите в профиле и возвращайтесь сюда.",
                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                    )
                } else if (!loaded) {
                    Text("…", color = DarkGray, fontSize = 13.sp)
                } else {
                    Text(
                        "Имя и зал увидит ученик, решая, доверять ли вам свой " +
                                "дневник. Это единственное, что он о вас узнает, — " +
                                "поэтому напишите так, как вас знают в зале.",
                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Звание само по себе не даёт ничего: пока ученик не откроет " +
                                "доступ отдельным решением, вы видите ровно то же, " +
                                "что любой друг.",
                        color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                    )

                    Spacer(Modifier.height(14.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 120) name = it },
                        label = { Text("Имя и фамилия", color = DarkGray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        colors = fields()
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = gym,
                        onValueChange = { if (it.length <= 120) gym = it },
                        label = { Text("Зал", color = DarkGray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        colors = fields()
                    )

                    Spacer(Modifier.height(16.dp))
                    WideButton(
                        text = if (card != null) "СОХРАНИТЬ" else "СТАТЬ ТРЕНЕРОМ",
                        enabled = !busy && name.trim().length >= 3 && gym.trim().length >= 2
                    ) {
                        scope.launch {
                            busy = true
                            message = null
                            when (val r = api.becomeCoach(name.trim(), gym.trim())) {
                                is SyncApi.Result.Fail -> message = r.message
                                is SyncApi.Result.Ok -> {
                                    card = r.data
                                    message = "Готово. Теперь ученик видит вашу карточку " +
                                            "в списке друзей."
                                    /* Список учеников подтягиваем сразу.

                                       Раньше он загружался только при
                                       открытии окна и только если человек
                                       УЖЕ был тренером. Стал им сейчас —
                                       и видел пустоту до следующего
                                       захода, будто ничего не вышло. */
                                    when (val st = api.students()) {
                                        is SyncApi.Result.Ok -> students = st.data
                                        is SyncApi.Result.Fail -> Unit
                                    }
                                    Refresh.bump()
                                }
                            }
                            busy = false
                        }
                    }

                    students?.let { list ->
                        Spacer(Modifier.height(18.dp))
                        Text("ВАШИ УЧЕНИКИ", color = DarkGray, fontSize = 10.sp,
                            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(8.dp))
                        if (list.isEmpty()) {
                            Text(
                                "Пока никого. Ученик появляется здесь сам, когда " +
                                        "в своём списке друзей нажмёт «Сделать моим тренером».",
                                color = DarkGray, fontSize = 12.sp, lineHeight = 17.sp
                            )
                        } else {
                            list.forEach { st ->
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(SoftGray, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text("@${st.handle}", color = Black, fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold)
                                    Text(
                                        if (st.body) "дневник и замеры тела" else "только дневник",
                                        color = DarkGray, fontSize = 11.sp
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }

                    if (card != null) {
                        Spacer(Modifier.height(14.dp))
                        if (!quitting) {
                            RowAction("Перестать быть тренером", enabled = !busy) {
                                quitting = true
                            }
                        } else {
                            Text(
                                "Ученики останутся в друзьях, но вы перестанете видеть " +
                                        "их дневники и планировать за них. Их согласия при " +
                                        "этом не стираются: вернётесь — вернутся и они.",
                                color = Black, fontSize = 12.sp, lineHeight = 16.sp
                            )
                            ActionRow {
                                RowAction("Отмена", enabled = !busy, strong = true) {
                                    quitting = false
                                }
                                RowAction("Да, перестать", enabled = !busy) {
                                    run {
                                        quitting = false
                                        scope.launch {
                                            busy = true
                                            when (val r = api.quitCoach()) {
                                                is SyncApi.Result.Fail -> message = r.message
                                                is SyncApi.Result.Ok -> {
                                                    card = null; students = null
                                                    name = ""; gym = ""
                                                    Refresh.bump()
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

                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Black, fontSize = 12.sp, lineHeight = 17.sp)
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RowAction("Закрыть", enabled = !busy, strong = true) { onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun fields() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Black, unfocusedBorderColor = LightGray, cursorColor = Black,
    focusedTextColor = Black, unfocusedTextColor = Black, focusedLabelColor = Black
)
