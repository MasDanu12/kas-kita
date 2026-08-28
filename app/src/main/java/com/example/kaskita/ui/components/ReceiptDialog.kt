package com.example.kaskita.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kaskita.data.model.ReceiptData
import com.example.kaskita.ui.theme.*
import com.example.kaskita.util.CurrencyFormatter
import com.example.kaskita.util.DateUtils

@Composable
fun ReceiptDialog(
    receipt: ReceiptData,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val isMasuk = receipt.tipe == "masuk"
    val amountColor = if (isMasuk) MasukGreen else KeluarRed
    val sign = if (isMasuk) "+ " else "- "

    val shareText = buildString {
        appendLine("📜 BUKTI TRANSAKSI KAS")
        appendLine("🏛️ ${receipt.organisasiNama}")
        appendLine("-----------------------------")
        appendLine("📅 Tanggal: ${DateUtils.formatDisplayDate(receipt.tanggal)}")
        appendLine("🏷️ Jenis: ${if (isMasuk) "Pemasukan" else "Pengeluaran"}")
        receipt.kategori?.let { appendLine("📂 Kategori: $it") }
        receipt.anggotaNama?.let { appendLine("👤 Anggota: $it") }
        if (receipt.periodeList.isNotEmpty()) {
            val pStr = if (receipt.periodeList.size == 1) receipt.periodeList.first()
            else "${receipt.periodeList.first()} s/d ${receipt.periodeList.last()}"
            appendLine("🗓️ Periode: $pStr")
        }
        receipt.akunNama?.let { appendLine("💳 Akun: $it") }
        receipt.catatan?.let { appendLine("📝 Catatan: $it") }
        appendLine("-----------------------------")
        appendLine("💰 Total: $sign${CurrencyFormatter.format(receipt.jumlah)}")
        appendLine("-----------------------------")
        appendLine("Dicatat via Kas Kita")
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bukti Transaksi",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Receipt Slip Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = receipt.organisasiNama,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "KAS KITA LEDGER",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))

                        ReceiptLine(label = "Tanggal", value = DateUtils.formatDisplayDate(receipt.tanggal))
                        ReceiptLine(label = "Tipe", value = if (isMasuk) "Pemasukan" else "Pengeluaran")
                        receipt.kategori?.let { ReceiptLine(label = "Kategori", value = it) }
                        receipt.anggotaNama?.let { ReceiptLine(label = "Anggota", value = it) }
                        if (receipt.periodeList.isNotEmpty()) {
                            val pStr = if (receipt.periodeList.size == 1) receipt.periodeList.first()
                            else "${receipt.periodeList.first()} s/d ${receipt.periodeList.last()}"
                            ReceiptLine(label = "Periode", value = pStr)
                        }
                        receipt.akunNama?.let { ReceiptLine(label = "Akun Kas", value = it) }
                        receipt.catatan?.let { ReceiptLine(label = "Catatan", value = it) }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "$sign${CurrencyFormatter.format(receipt.jumlah)}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = amountColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Selesai")
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, shareText)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Bagikan Bukti Transaksi"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bagikan")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiptLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}
