package ru.styrk.app

import android.content.Intent
import android.content.pm.PackageManager
import android.view.inputmethod.InputMethodManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.graphics.Color as AndroidColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.styrk.app.notifications.ReminderScheduler
import ru.styrk.app.ui.main.MainScreen
import ru.styrk.app.ui.theme.StyrkTheme

/**
 * Счётчик возвращений в приложение.
 *
 * Зачем он нужен. Дневник живёт в двух местах — в приложении
 * и в мини-приложении Телеграма, — и человек ходит между ними
 * туда-сюда. Записал тренировку в браузере, вернулся в приложение,
 * а её там нет: обмен с сервером был только при запуске, а запуска
 * не было — приложение висело свёрнутым.
 *
 * Почему счётчик, а не обмен прямо здесь. Экран после обмена должен
 * перечитать данные, а про экран знает не Activity, а MainScreen:
 * там живёт и ViewModel, и уже написанный обмен. Activity только
 * сообщает «мы вернулись», остальное делает тот, кто умеет.
 *
 * Почему не Lifecycle из Compose. Он в свежих версиях переехал
 * в отдельную библиотеку, которой в проекте нет, а `onResume`
 * у Activity есть всегда и ведёт себя одинаково на любом Android.
 */
/**
 * Ссылки, которыми открыли приложение.
 *
 * Пока одна: styrk.ru/reset-password?token=… из письма «Восстановление
 * пароля». Activity кладёт сюда знак из ссылки, MainScreen показывает
 * окно «Новый пароль» и, когда оно закрыто, обнуляет.
 */
object DeepLinks {
    var resetToken by mutableStateOf<String?>(null)

    fun take(intent: Intent?) {
        val u = intent?.data ?: return
        if (intent.action != Intent.ACTION_VIEW) return
        if (u.scheme != "https" || u.host != "styrk.ru") return
        if (u.path?.startsWith("/reset-password") != true) return
        val t = u.getQueryParameter("token")?.trim().orEmpty()
        // Знак — 43 знака base64url. Всё прочее — не наша ссылка.
        if (t.length in 20..200 && t.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            resetToken = t
        }
    }
}

object AppResumed {
    private val _ticks = MutableStateFlow(0)
    val ticks: StateFlow<Int> = _ticks.asStateFlow()
    fun bump() { _ticks.value = _ticks.value + 1 }
}

class MainActivity : ComponentActivity() {

    // Запрос разрешения на уведомления (обязательно с Android 13 / API 33).
    // Если пользователь откажет — приложение работает как раньше,
    // просто без таймера в шторке и без напоминаний.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ВАЖНО: начиная с targetSdk 35 Android принудительно включает
        // edge-to-edge для всех приложений — ручная установка
        // window.statusBarColor перестаёт действовать, и без явной
        // инициализации через enableEdgeToEdge() система может сама
        // решать, тёмные или светлые иконки статус-бара показывать,
        // ориентируясь на системную тему телефона (а не на наш экран).
        // Именно поэтому на чёрных экранах при светлой системной теме
        // иконки были невидимыми. enableEdgeToEdge() правильно
        // инициализирует edge-to-edge с самого начала, после чего наши
        // рантайм-переключения иконок (SystemBarsAppearance) в Compose
        // снова работают предсказуемо на всех версиях Android.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                AndroidColor.TRANSPARENT,
                AndroidColor.TRANSPARENT
            )
        )

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        // Планировщик напоминаний: единожды передаём application context,
        // дальше им пользуется ViewModel. Плюс ежедневная проверка
        // «не было тренировок больше 2 дней» (~19:00, живёт в WorkManager,
        // переживает перезагрузку телефона).
        ReminderScheduler.init(applicationContext)
        ReminderScheduler.ensureDailyInactivityCheck()

        // УДАЛЕНО: здесь создавались WorkoutRepository и WorkoutViewModel
        // и передавались в MainScreen. Остатки самого раннего каркаса
        // приложения: ни один экран этой ViewModel не пользовался, она
        // просто протаскивалась через MainScreen в AppNavigation и там
        // игнорировалась. Вместе с ней ушли три таблицы, в которые
        // никогда ничего не записывалось.
        //
        // Базу здесь открывать тоже не нужно: MainScreen открывает её
        // сам, когда создаёт TrainingSessionViewModel.

        // Открыли ссылкой из письма — запомним знак до первой отрисовки.
        // При повороте экрана Activity создаётся заново с тем же
        // намерением, поэтому только при первом создании.
        if (savedInstanceState == null) DeepLinks.take(intent)

        setContent {
            StyrkTheme {
                MainScreen()
            }
        }
    }

    /* Вернулись в приложение — сообщаем об этом экрану, он спросит
       сервер. Вызывается и при первом показе, и после каждого
       возвращения из другого приложения. */
    override fun onResume() {
        super.onResume()
        AppResumed.bump()
    }

    /* Клавиатура, оставшаяся от закрытого окна.

       Окно с полем ввода (пожелание тренеру, вес, заметка) закрыли —
       а клавиатура на некоторых телефонах оставалась висеть: её держало
       поле, которого уже нет. Под ней можно было ходить по вкладкам,
       и убрать её получалось только системной «Назад».

       Когда главное окно снова получает фокус — значит, всплывающее
       закрылось, — смотрим: стоит ли курсор в поле ввода. Не стоит —
       прячем клавиатуру сами. Если человек как раз печатает в поле
       на экране, ничего не трогаем. */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val decor = window?.decorView ?: return
        decor.postDelayed({
            val focused = decor.findFocus()
            if (focused == null || !focused.onCheckIsTextEditor()) {
                getSystemService(InputMethodManager::class.java)
                    ?.hideSoftInputFromWindow(decor.windowToken, 0)
            }
        }, 150)
    }

    /* Приложение уже было открыто (singleTop) — ссылка приходит сюда,
       а не в onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinks.take(intent)
    }
}
