package ru.styrk.app.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val title: String,
    val icon: ImageVector
) {

    object Training : BottomNavItem("training", "Тренировка", Icons.Default.FitnessCenter)

    // Заменено на EmojiEvents (кубок) — соответствует иконографии
    // рекордов, используемой во всём остальном приложении (🏆)
    object Records : BottomNavItem("records", "Рекорды", Icons.Default.EmojiEvents)

    object Calendar : BottomNavItem("calendar", "Календарь", Icons.Default.CalendarMonth)

    object Info : BottomNavItem("info", "Инфо", Icons.Default.Info)

    // ДОБАВЛЕНО (этап «профиль»): 5-я вкладка
    object Profile : BottomNavItem("profile", "Профиль", Icons.Default.Person)
}