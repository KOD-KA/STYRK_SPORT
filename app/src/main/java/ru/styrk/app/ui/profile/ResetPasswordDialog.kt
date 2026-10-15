package ru.styrk.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import ru.styrk.app.ui.common.RowAction
import ru.styrk.app.ui.common.WideButton
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White

/**
 * «Новый пароль» — открывается ссылкой из письма (App Links).
 *
 * Ссылка одноразовая и живёт час. После смены сервер закрывает все
 * прежние входы и выдаёт этому телефону новый ключ — поэтому здесь
 * же и входим, без второго ввода пароля, и сразу делаем обычный обмен
 * (объединение: он ничего не теряет).
 */
@Composable
fun ResetPasswordDialog(token: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SyncPrefs(context) }
    val api = remember { SyncApi(prefs) }
    val scope = rememberCoroutineScope()

    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = { if (!busy) onDone() }) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(White)
        ) {
            Column(
                Modifier.fillMaxWidth().background(Black)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text("НОВЫЙ ПАРОЛЬ", color = White, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (done) "Готово. Вы вошли с новым паролем."
                    else "Придумайте пароль от восьми знаков. Прежние входы закроются.",
                    color = White.copy(.62f), fontSize = 12.sp, lineHeight = 17.sp
                )
            }
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                if (!done) {
                    PassField(p1, { p1 = it }, "Новый пароль")
                    Spacer(Modifier.height(10.dp))
                    PassField(p2, { p2 = it }, "Ещё раз")
                    Spacer(Modifier.height(16.dp))
                    WideButton(
                        text = if (busy) "СОЕДИНЯЮСЬ…" else "СМЕНИТЬ ПАРОЛЬ",
                        enabled = !busy && p1.length >= 8 && p2.isNotEmpty()
                    ) {
                        if (p1 != p2) { message = "Пароли не совпадают"; return@WideButton }
                        busy = true; message = null
                        scope.launch {
                            when (val r = api.resetPasswordByToken(token, p1)) {
                                is SyncApi.Result.Ok -> {
                                    prefs.token = r.data.token
                                    prefs.email = r.data.email
                                    prefs.markReconciled(r.data.email)
                                    done = true
                                    message = "На других устройствах войдите заново с новым паролем."
                                    val db = AppDatabaseProvider.getDatabase(context)
                                    SyncManager(context, db).sync()
                                }
                                is SyncApi.Result.Fail -> message =
                                    if (r.error == "reset_expired")
                                        "Ссылка устарела или уже использована. Запросите письмо ещё раз: " +
                                                "Профиль → Аккаунт → «Забыли пароль?»."
                                    else r.message
                            }
                            busy = false
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    if (p1.isNotEmpty() && p1.length < 8) {
                        Text("Не короче восьми знаков", color = DarkGray, fontSize = 11.sp)
                    }
                }
                message?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Black, fontSize = 12.sp, lineHeight = 16.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    RowAction(if (done) "Готово" else "Отмена", enabled = !busy, strong = done) { onDone() }
                }
            }
        }
    }
}

@Composable
private fun PassField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label, color = DarkGray) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Black, unfocusedBorderColor = SoftGray, cursorColor = Black,
            focusedTextColor = Black, unfocusedTextColor = Black, focusedLabelColor = Black
        )
    )
}
