package ru.styrk.app.ui.common

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.styrk.app.data.local.database.AppDatabaseProvider
import ru.styrk.app.sync.SyncApi
import ru.styrk.app.sync.SyncManager
import ru.styrk.app.sync.SyncPrefs
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White
import java.util.TimeZone

/**
 * Кнопка «Поделиться» в тёмной шапке окна тренировки или плана.
 *
 * Наверху, а не в конце списка: у длинной тренировки низ окна —
 * это десять экранов прокрутки, и до кнопки там никто не доходит.
 */
@Composable
fun ShareChip(onClick: () -> Unit) {
    Text(
        "Поделиться",
        color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        maxLines = 1, softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

/**
 * Окно «Поделиться»: куда отправить тренировку или план.
 *
 * Способы:
 *  - Телеграм — бот STYRK присылает карточку с таблицей подходов в чат
 *    с ним, дальше её пересылают. Только для проведённой тренировки
 *    ([workoutUuid] не пустой): карточку собирает сервер из записи;
 *  - «Отправить…» — системное окно: мессенджеры, заметки, что угодно;
 *  - почта — письмо с текстом и темой;
 *  - файл .txt — человек сам выбирает, куда сохранить;
 *  - копия в буфер обмена.
 *
 * Окно — обычное белое, как остальные окна приложения. Поверх окна
 * тренировки снизу второе такое же «снизу» было бы уже тесно.
 */
@Composable
fun ShareDialog(
    subject: String,
    text: String,
    fileName: String,
    workoutUuid: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // Куда сохранить файл, выбирает человек: «Загрузки», диск, заметки.
    // Отдельного разрешения на память так не нужно.
    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } ?: error("нет потока")
        }.isSuccess
        if (ok) {
            Toast.makeText(context, "Файл сохранён", Toast.LENGTH_SHORT).show()
            onDismiss()
        } else status = "Не удалось сохранить файл"
    }

    fun telegram() {
        val uuid = workoutUuid?.takeIf { it.isNotBlank() } ?: return
        val prefs = SyncPrefs(context)
        if (!prefs.isSignedIn) {
            status = "Нужен аккаунт: Профиль → Аккаунт. Карточку присылает бот STYRK — " +
                    "ему нужно знать, кому."
            return
        }
        busy = true
        status = "Отправляю…"
        scope.launch {
            val api = SyncApi(prefs)
            val tz = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60000
            var r = api.shareSend(uuid, tz)
            // Тренировка могла ещё не уехать на сервер: обмен и вторая попытка.
            // Просить «подождите и нажмите ещё раз» незачем — сделать сам
            // человек тут ничего не может.
            if (r is SyncApi.Result.Fail && r.code == 404) {
                status = "Сначала отправляю тренировку на сервер…"
                SyncManager(context, AppDatabaseProvider.getDatabase(context)).sync()
                r = api.shareSend(uuid, tz)
            }
            busy = false
            when (r) {
                is SyncApi.Result.Ok -> {
                    Toast.makeText(context, "Карточка в чате с ботом — перешлите её",
                        Toast.LENGTH_LONG).show()
                    openLink(context, r.data)
                    onDismiss()
                }
                is SyncApi.Result.Fail -> status = when (r.error) {
                    "no_telegram" -> "Телеграм не привязан к аккаунту. Откройте бота @styrk_bot → " +
                            "«Открыть дневник» → Аккаунт → «Уже есть дневник — привязать»."
                    "too_often" -> "Слишком часто — попробуйте через несколько минут."
                    "tg_disabled" -> "Телеграм на сервере сейчас выключен."
                    else -> r.message
                }
            }
        }
    }

    fun system() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(Intent.createChooser(send, "Поделиться"))
            onDismiss()
        } catch (_: ActivityNotFoundException) {
            status = "Отправить нечем — скопируйте текст"
        }
    }

    fun mail() {
        // И в адресе, и в дополнениях: одни почтовые читают одно, другие — другое.
        val uri = Uri.parse("mailto:?subject=" + Uri.encode(subject) + "&body=" + Uri.encode(text))
        val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        try {
            context.startActivity(intent)
            onDismiss()
        } catch (_: ActivityNotFoundException) {
            status = "Почтового приложения нет — выберите «Отправить…» или скопируйте текст"
        }
    }

    fun copy() {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (cm == null) { status = "Не удалось скопировать"; return }
        cm.setPrimaryClip(ClipData.newPlainText("STYRK", text))
        // С Android 13 система сама показывает, что скопировано, —
        // второе сообщение поверх было бы лишним.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show()
        }
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = White,
        titleContentColor = Black,
        textContentColor = DarkGray,
        title = { Text("Поделиться", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(subject, color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(10.dp))
                if (!workoutUuid.isNullOrBlank()) {
                    ShareRow("✈️", "В Телеграм",
                        "Бот STYRK пришлёт карточку с таблицей подходов — останется переслать",
                        enabled = !busy) { telegram() }
                }
                ShareRow("📤", "Отправить…", "Мессенджеры, заметки — всё, что есть на телефоне",
                    enabled = !busy) { system() }
                ShareRow("✉️", "На почту", "Письмо с тренировкой текстом", enabled = !busy) { mail() }
                ShareRow("📄", "Сохранить файлом", "Текстовый файл .txt — для заметок и архива",
                    enabled = !busy) { saveFile.launch(fileName) }
                ShareRow("📋", "Скопировать текст", "Вставить куда угодно", enabled = !busy) { copy() }
                status?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Black, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть", color = Black) }
        }
    )
}

@Composable
private fun ShareRow(icon: String, title: String, sub: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).background(SoftGray, CircleShape), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 17.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) Black else DarkGray, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold)
            Text(sub, color = DarkGray, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** Ссылку открывает Telegram, если он стоит, иначе браузер. */
private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: Exception) {
        Toast.makeText(context, "Откройте @styrk_bot в Телеграме", Toast.LENGTH_LONG).show()
    }
}
