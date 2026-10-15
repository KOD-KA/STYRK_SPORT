package ru.styrk.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.styrk.app.data.model.MuscleGroup
import ru.styrk.app.ui.theme.*
import androidx.compose.ui.text.style.TextAlign

@Composable
fun MuscleGroupScreen(
    onBack: () -> Unit,
    onGroupSelected: (MuscleGroup) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {

        // ===== ШАПКА С КНОПКОЙ НАЗАД =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Black)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Text("←", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "ВЫБЕРИТЕ ГРУППУ МЫШЦ",
                color = White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )
        }

        // ===== СЕТКА ГРУПП МЫШЦ (2 колонки) =====
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(MuscleGroup.visible) { group ->
                Box(
                    modifier = Modifier
                        // Высота минимальная, а не жёсткая: «Предплечья»
                        // и «Жиросжигание» — самые длинные названия,
                        // и при крупном шрифте они переносятся на две
                        // строки. В карточке ростом ровно 90 точек
                        // вторая строка обрезалась по краю.
                        .heightIn(min = 90.dp)
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(12.dp))
                        .background(color = SurfaceLight, shape = RoundedCornerShape(12.dp))
                        .clickable { onGroupSelected(group) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = group.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Black,
                        // По центру и с отступами: без них длинное
                        // название прижималось к самому краю карточки
                        // и выглядело обрезанным, даже когда влезало.
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}