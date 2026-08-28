package com.example.kaskita.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaskita.data.model.TransaksiWithDetails
import com.example.kaskita.ui.theme.*
import com.example.kaskita.ui.viewmodel.KasKitaViewModel
import com.example.kaskita.util.CurrencyFormatter
import com.example.kaskita.util.DateUtils

@Composable
fun HomeScreen(
    viewModel: KasKitaViewModel,
    onOpenTransactionDialog: (String) -> Unit,
    onOpenPayDuesDialog: () -> Unit
) {
    val accounts by viewModel.accountsWithSaldo.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val duesOverview by viewModel.duesOverview.collectAsState()
    val currentMonth = DateUtils.currentPeriode()

    // Calculate total saldo and current month income & expense
    val totalSaldo = accounts.sumOf { it.saldo }
    val monthTrx = transactions.filter { it.tanggal.startsWith(currentMonth) }
    val totalMasukBulanIni = monthTrx.filter { it.tipe == "masuk" }.sumOf { it.jumlah }
    val totalKeluarBulanIni = monthTrx.filter { it.tipe == "keluar" }.sumOf { it.jumlah }
    val recentTrx = transactions.take(5)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp)
    ) {
        // Balance Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_balance_card"),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(PineGreen, Color(0xFF17694F))
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        Text(
                            text = "Saldo Kas Saat Ini",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = CurrencyFormatter.format(totalSaldo),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Pemasukan",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = CurrencyFormatter.format(totalMasukBulanIni),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .padding(10.dp)
                            ) {
                                Column {
                                    Text(
                                        text = "Pengeluaran",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                    Text(
                                        text = CurrencyFormatter.format(totalKeluarBulanIni),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Actions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onOpenTransactionDialog("masuk") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_income_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PineGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Masuk", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onOpenTransactionDialog("keluar") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_quick_expense_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KeluarRed)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("+ Keluar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { onOpenPayDuesDialog() },
                    modifier = Modifier
                        .weight(1.2f)
                        .testTag("home_quick_dues_btn"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = PineGreen
                    )
                ) {
                    Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Bayar Iuran", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Dues Summary Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_dues_summary_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Iuran Bulan Ini",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${duesOverview?.totalAnggota ?: 0} anggota aktif",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        TextButton(
                            onClick = { viewModel.selectTab("iuran") },
                            modifier = Modifier.testTag("home_view_dues_btn")
                        ) {
                            Text("Kelola", fontSize = 13.sp, color = PineGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DuesStatBox(
                            title = "Lunas",
                            value = duesOverview?.lunas?.toString() ?: "0",
                            color = MasukGreen,
                            modifier = Modifier.weight(1f)
                        )
                        DuesStatBox(
                            title = "Sebagian",
                            value = duesOverview?.sebagian?.toString() ?: "0",
                            color = GoldAccent,
                            modifier = Modifier.weight(1f)
                        )
                        DuesStatBox(
                            title = "Belum Bayar",
                            value = duesOverview?.menunggak?.toString() ?: "0",
                            color = KeluarRed,
                            modifier = Modifier.weight(1f)
                        )
                        DuesStatBox(
                            title = "Tunggakan",
                            value = CurrencyFormatter.formatCompact(duesOverview?.tunggakan ?: 0.0),
                            color = KeluarRed,
                            modifier = Modifier.weight(1.3f)
                        )
                    }
                }
            }
        }

        // Recent Activities Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_recent_activities_card"),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aktivitas Terbaru",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        TextButton(
                            onClick = { viewModel.selectTab("kas") },
                            modifier = Modifier.testTag("home_view_all_transactions_btn")
                        ) {
                            Text("Lihat Semua", fontSize = 13.sp, color = PineGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (recentTrx.isEmpty()) {
                        Text(
                            text = "Belum ada transaksi di organisasi ini",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    } else {
                        recentTrx.forEach { item ->
                            RecentTransactionItem(
                                trx = item,
                                onShowReceipt = { viewModel.showReceiptForTransaction(item.id) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuesStatBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = title,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentTransactionItem(
    trx: TransaksiWithDetails,
    onShowReceipt: () -> Unit
) {
    val isMasuk = trx.tipe == "masuk"
    val isKeluar = trx.tipe == "keluar"
    val amountColor = when {
        isMasuk -> MasukGreen
        isKeluar -> KeluarRed
        else -> MaterialTheme.colorScheme.onSurface
    }
    val sign = when {
        isMasuk -> "+"
        isKeluar -> "-"
        trx.tipe == "penyesuaian" && trx.jumlah < 0 -> "-"
        trx.tipe == "penyesuaian" -> "+"
        else -> ""
    }

    val title = trx.kategori ?: when (trx.tipe) {
        "masuk" -> "Pemasukan"
        "keluar" -> "Pengeluaran"
        "transfer" -> "Transfer"
        else -> "Penyesuaian"
    }

    var sub = DateUtils.formatDisplayDate(trx.tanggal)
    trx.anggotaNama?.let { sub += " · $it" }
    if (trx.tipe == "transfer") {
        sub += " · ${trx.akunNama ?: "-"} → ${trx.akunTujuanNama ?: "-"}"
    } else if (!trx.catatan.isNullOrBlank()) {
        sub += " · ${trx.catatan}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        when (trx.tipe) {
                            "masuk" -> MasukGreenLight
                            "keluar" -> KeluarRedLight
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (trx.tipe) {
                        "masuk" -> Icons.Default.ArrowDownward
                        "keluar" -> Icons.Default.ArrowUpward
                        "transfer" -> Icons.Default.SwapHoriz
                        else -> Icons.Default.Tune
                    },
                    contentDescription = null,
                    tint = amountColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (trx.sumber == "iuran") {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(GoldContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Iuran", fontSize = 10.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = sub,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "$sign${CurrencyFormatter.format(Math.abs(trx.jumlah))}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = amountColor
            )
            Text(
                text = "Struk",
                fontSize = 11.5.sp,
                color = PineGreen,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onShowReceipt() }
                    .padding(top = 2.dp)
            )
        }
    }
}
