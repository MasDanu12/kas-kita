package com.example.kaskita.ui.screens.reports

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaskita.data.model.TransaksiWithDetails
import com.example.kaskita.ui.components.AnnualTrendBarChart
import com.example.kaskita.ui.components.ExpenseDonutChart
import com.example.kaskita.ui.theme.*
import com.example.kaskita.ui.viewmodel.KasKitaViewModel
import com.example.kaskita.util.CurrencyFormatter
import com.example.kaskita.util.DateUtils

@Composable
fun LaporanScreen(viewModel: KasKitaViewModel) {
    val laporanTab by viewModel.laporanTab.collectAsState()
    val selectedBulan by viewModel.laporanSelectedBulan.collectAsState()
    val selectedTahun by viewModel.laporanSelectedTahun.collectAsState()
    val monthlyData by viewModel.monthlyReport.collectAsState()
    val annualData by viewModel.annualReport.collectAsState()
    val currentOrg by viewModel.currentOrg.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Tab switcher (Bulanan / Tahunan)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (laporanTab == "bulanan") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer)
                    .clickable { viewModel.setLaporanTab("bulanan") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Laporan Bulanan",
                    fontSize = 13.sp,
                    fontWeight = if (laporanTab == "bulanan") FontWeight.Bold else FontWeight.Normal,
                    color = if (laporanTab == "bulanan") PineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (laporanTab == "tahunan") MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer)
                    .clickable { viewModel.setLaporanTab("tahunan") }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Laporan Tahunan",
                    fontSize = 13.sp,
                    fontWeight = if (laporanTab == "tahunan") FontWeight.Bold else FontWeight.Normal,
                    color = if (laporanTab == "tahunan") PineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (laporanTab == "bulanan") {
            // Period selector for Monthly
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.setLaporanBulan(DateUtils.periodAdd(selectedBulan, -1)) }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Bulan Sebelumnya")
                    }
                    Text(
                        text = DateUtils.formatMonthYear(selectedBulan),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PineGreen
                    )
                    IconButton(onClick = { viewModel.setLaporanBulan(DateUtils.periodAdd(selectedBulan, 1)) }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Bulan Berikutnya")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            monthlyData?.let { report ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Summary 4-card grid
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportMetricCard(
                                    title = "Pemasukan",
                                    value = CurrencyFormatter.format(report.totalMasuk),
                                    color = MasukGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportMetricCard(
                                    title = "Pengeluaran",
                                    value = CurrencyFormatter.format(report.totalKeluar),
                                    color = KeluarRed,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportMetricCard(
                                    title = "Saldo Bersih (Net)",
                                    value = CurrencyFormatter.format(report.saldoBersih),
                                    color = if (report.saldoBersih >= 0) MasukGreen else KeluarRed,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportMetricCard(
                                    title = "Jumlah Transaksi",
                                    value = "${report.jumlahTransaksi} transaksi",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Share button
                    item {
                        Button(
                            onClick = {
                                val shareText = buildString {
                                    appendLine("📊 LAPORAN KAS BULANAN")
                                    appendLine("🏛️ ${currentOrg?.nama ?: "Kas Kita"}")
                                    appendLine("📅 Periode: ${DateUtils.formatMonthYear(report.bulan)}")
                                    appendLine("-----------------------------")
                                    appendLine("🟢 Pemasukan: ${CurrencyFormatter.format(report.totalMasuk)}")
                                    appendLine("🔴 Pengeluaran: ${CurrencyFormatter.format(report.totalKeluar)}")
                                    appendLine("💰 Saldo Bersih: ${CurrencyFormatter.format(report.saldoBersih)}")
                                    appendLine("📝 Transaksi: ${report.jumlahTransaksi}")
                                    appendLine("-----------------------------")
                                    appendLine("🏷️ Pengeluaran Terbesar:")
                                    report.kategoriExpenses.take(3).forEach {
                                        appendLine("• ${it.nama}: ${CurrencyFormatter.format(it.total)}")
                                    }
                                    appendLine("-----------------------------")
                                    appendLine("Dicatat via Kas Kita")
                                }
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Bagikan Laporan Kas"))
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PineGreen)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bagikan Ringkasan Laporan", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Expense Breakdown Chart Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Rincian Pengeluaran",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                ExpenseDonutChart(categories = report.kategoriExpenses)
                            }
                        }
                    }

                    // Itemized Transactions Header
                    item {
                        Text(
                            text = "Daftar Transaksi Periode Ini",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    if (report.transaksiList.isEmpty()) {
                        item {
                            Text(
                                text = "Tidak ada transaksi di bulan ini",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(report.transaksiList) { t ->
                            ReportTransactionRow(trx = t)
                        }
                    }
                }
            }
        } else {
            // Annual Report View
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val prevYear = (selectedTahun.toInt() - 1).toString()
                        viewModel.setLaporanTahun(prevYear)
                    }) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Tahun Sebelumnya")
                    }
                    Text(
                        text = "Tahun $selectedTahun",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PineGreen
                    )
                    IconButton(onClick = {
                        val nextYear = (selectedTahun.toInt() + 1).toString()
                        viewModel.setLaporanTahun(nextYear)
                    }) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Tahun Berikutnya")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            annualData?.let { report ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportMetricCard(
                                    title = "Total Pemasukan",
                                    value = CurrencyFormatter.format(report.totalMasuk),
                                    color = MasukGreen,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportMetricCard(
                                    title = "Total Pengeluaran",
                                    value = CurrencyFormatter.format(report.totalKeluar),
                                    color = KeluarRed,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ReportMetricCard(
                                    title = "Saldo Bersih Tahunan",
                                    value = CurrencyFormatter.format(report.saldoBersih),
                                    color = if (report.saldoBersih >= 0) MasukGreen else KeluarRed,
                                    modifier = Modifier.weight(1f)
                                )
                                ReportMetricCard(
                                    title = "Total Transaksi",
                                    value = "${report.jumlahTransaksi} transaksi",
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Annual Bar Chart Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Tren Arus Kas Bulanan",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                AnnualTrendBarChart(trends = report.monthsTrend)
                            }
                        }
                    }

                    // Monthly Breakdown Table
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Rangkuman Tiap Bulan",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                report.monthsTrend.forEach { m ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(m.bulanLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                                        Text("+${CurrencyFormatter.formatCompact(m.masuk)}", fontSize = 12.sp, color = MasukGreen)
                                        Text("-${CurrencyFormatter.formatCompact(m.keluar)}", fontSize = 12.sp, color = KeluarRed)
                                        Text(
                                            CurrencyFormatter.formatCompact(m.net),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (m.net >= 0) MasukGreen else KeluarRed
                                        )
                                    }
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportMetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(text = title, fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
private fun ReportTransactionRow(trx: TransaksiWithDetails) {
    val isMasuk = trx.tipe == "masuk"
    val isKeluar = trx.tipe == "keluar"
    val color = when {
        isMasuk -> MasukGreen
        isKeluar -> KeluarRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    val sign = if (isMasuk) "+" else if (isKeluar) "-" else ""

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trx.kategori ?: trx.tipe.capitalize(),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${DateUtils.formatDisplayDate(trx.tanggal)}${if (!trx.catatan.isNullOrBlank()) " · " + trx.catatan else ""}",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "$sign${CurrencyFormatter.format(trx.jumlah)}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}
