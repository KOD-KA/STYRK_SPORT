package ru.styrk.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.navigation.BottomNavItem
import ru.styrk.app.ui.common.CHROME_FONT_CAP
import ru.styrk.app.ui.common.CappedFontScale
import ru.styrk.app.ui.theme.Black
import ru.styrk.app.ui.theme.DarkGray
import ru.styrk.app.ui.theme.SoftGray
import ru.styrk.app.ui.theme.White

/**
 * Нижняя навигация в едином стиле приложения: белый фон, тонкий разделитель
 * сверху, у выбранной вкладки — чёрный кружок под иконкой с плавной
 * анимацией и жирная подпись. У остальных — просто серая иконка и подпись.
 *
 * ИЗМЕНЕНО: раньше активная вкладка определялась по route строке из
 * NavController. Теперь экраны переключаются через HorizontalPager (это
 * даёт свайп между вкладками), поэтому активная вкладка определяется по
 * текущей странице пейджера (selectedIndex).
 */
@Composable
fun StyrkBottomBar(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onItemClick: (Int) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(White)) {

        HorizontalDivider(color = SoftGray, thickness = 1.dp)

        // Потолок масштаба — только здесь, у панели. Высота её задана,
        // и подпись, переехавшая на вторую строку, ломает всю разметку.
        // Содержимое экранов при этом масштабируется как угодно.
        CappedFontScale(CHROME_FONT_CAP) {
            // Ширина по содержимому, а НЕ поровну.
            //
            // Делить поровну кажется честнее, но здесь это хуже: пятая
            // часть узкого экрана — около 56 точек, а «Тренировка» просит
            // 61. Подпись обрывалась бы даже при обычном шрифте.
            //
            // При размещении по содержимому «Инфо» отдаёт свой запас
            // «Тренировке», и ряд помещается целиком. Проверено расчётом:
            // сумма всех пяти подписей влезает в 320 точек с потолком 1,15.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                items.forEachIndexed { index, item ->
                    BottomBarItem(
                        item = item,
                        selected = index == selectedIndex,
                        onClick = { onItemClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    item: BottomNavItem,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val circleColor by animateColorAsState(
        targetValue = if (selected) Black else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(200),
        label = "bottomBarCircle"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) White else DarkGray,
        animationSpec = tween(200),
        label = "bottomBarIcon"
    )
    val circleSize by animateDpAsState(
        targetValue = if (selected) 44.dp else 36.dp,
        animationSpec = tween(200),
        label = "bottomBarCircleSize"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            // 2 точки вместо 4: на узком экране эти двадцать точек
            // на весь ряд решают, влезет подпись или оборвётся.
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .background(circleColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.title,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) Black else DarkGray,
            // Ровно одна строка. Если подпись всё же не помещается,
            // пусть лучше оборвётся многоточием, чем переедет вниз:
            // обрезанное слово читается, разъехавшаяся панель — нет.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}