package ru.styrk.app.ui.theme

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Управляет ТОЛЬКО цветом системных иконок (время/батарея/жесты навигации).
 *
 * ВАЖНО-1: начиная с API 35 (targetSdk 35+) window.statusBarColor игнорируется
 * системой — цвет полосы статус-бара теперь рисует само приложение
 * (см. полосу-подложку в MainScreen.kt). Поэтому здесь остались только иконки.
 *
 * ВАЖНО-2: ЕДИНСТВЕННОЕ место вызова — MainScreen.kt. НЕ вызывать из
 * отдельных экранов: HorizontalPager компонует соседние страницы во время
 * свайпа, и несколько конкурирующих SideEffect перебивают друг друга —
 * побеждает случайный. Именно из-за этого иконки «периодически» пропадали
 * на чёрных экранах тренировки.
 *
 * darkIcons = true  -> тёмные иконки (для светлого фона статус-бара)
 * darkIcons = false -> светлые иконки (для тёмного фона статус-бара)
 */
@Composable
fun SystemBarsAppearance(
    darkIcons: Boolean
) {
    val view = LocalView.current
    if (view.isInEditMode) return

    val activity = view.context as? Activity ?: return

    SideEffect {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        controller.isAppearanceLightStatusBars = darkIcons
        // Нижняя панель навигации приложения всегда белая,
        // поэтому иконки/жесты внизу всегда тёмные — независимо от экрана.
        controller.isAppearanceLightNavigationBars = true
    }
}