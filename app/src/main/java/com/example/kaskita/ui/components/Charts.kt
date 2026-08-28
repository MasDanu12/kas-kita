package com.example.kaskita.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaskita.data.model.CategoryExpense
import com.example.kaskita.data.model.MonthTrend
import com.example.kaskita.ui.theme.KeluarRed
import com.example.kaskita.ui.theme.MasukGreen
import com.example.kaskita.util.CurrencyFormatter

val CategoryColors = listOf(
    Color(0xFFC0392B),
    Color(0xFFE67E22),
    Color(0xFFC9932F),
    Color(0xFF8E44AD),
    Color(0xFF2980B9),
    Color(0xFF16A085),
    Color(0xFF7F8C8D),
    Color(0xFFD35400),
    Color(0xFF2C3E50),
    Color(0xFF1C8A5A)
)

@Composable
fun ExpenseDonutChart(
    categories: List<CategoryExpense>,
    modifier: Modifier = Modifier
) {
    if (categories.isEmpty() || categories.all { it.total <= 0 }) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Belum ada pengeluaran pada periode ini",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val totalExpense = categories.sumOf { it.total }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Donut Canvas
        Box(
            modifier = Modifier
                .size(130.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                var startAngle = -90f
                categories.forEachIndexed { index, cat ->
                    val sweepAngle = cat.percentage * 360f
                    val color = CategoryColors[index % CategoryColors.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 24.dp.toPx(), cap = StrokeCap.Butt),
                        size = Size(size.width, size.height)
                    )
                    startAngle += sweepAngle
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Legend list
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            categories.take(5).forEachIndexed { index, cat ->
                val color = CategoryColors[index % CategoryColors.size]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .background(color, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cat.nama,
                            fontSize = 12.sp,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = CurrencyFormatter.formatCompact(cat.total),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun AnnualTrendBarChart(
    trends: List<MonthTrend>,
    modifier: Modifier = Modifier
) {
    if (trends.isEmpty()) return

    val maxVal = maxOf(1.0, trends.maxOf { maxOf(it.masuk, it.keluar) })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            trends.forEach { item ->
                val masukHeight = ((item.masuk / maxVal) * 95).dp
                val keluarHeight = ((item.keluar / maxVal) * 95).dp

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(100.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(maxOf(2.dp, masukHeight))
                                .background(MasukGreen, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .height(maxOf(2.dp, keluarHeight))
                                .background(KeluarRed, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.bulanLabel,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(MasukGreen, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Masuk", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(KeluarRed, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Keluar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
