package com.example.kaskita.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kaskita.data.model.MemberArrears
import com.example.kaskita.ui.theme.MasukGreen
import com.example.kaskita.util.CurrencyFormatter
import com.example.kaskita.util.DateUtils

@Composable
fun WhatsAppReminderDialog(
    arrears: MemberArrears,
    orgName: String,
    periode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    var messageText by remember {
        val defaultMsg = "Halo Bapak/Ibu ${arrears.nama},\n\n" +
                "Mengingatkan kembali perihal iuran di *${orgName}* untuk periode ${DateUtils.formatMonthYear(periode)}, " +
                "saat ini tercatat memiliki tunggakan sebesar *${CurrencyFormatter.format(arrears.totalTunggakan)}*.\n\n" +
                "Mohon kesediaannya untuk melakukan pembayaran melalui bendahara. Terima kasih banyak atas kerjasamanya! 🙏"
        mutableStateOf(defaultMsg)
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
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pengingat Iuran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Text(
                    text = "Kirim pesan pengingat ke ${arrears.nama} (${arrears.noHp ?: "No HP belum diisi"})",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    label = { Text("Teks Pesan") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, messageText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Kirim Pengingat"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Bagikan", fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val rawPhone = arrears.noHp?.replace("[^0-9]".toRegex(), "") ?: ""
                            val cleanPhone = if (rawPhone.startsWith("0")) {
                                "62" + rawPhone.substring(1)
                            } else if (rawPhone.startsWith("+62")) {
                                rawPhone.substring(1)
                            } else {
                                rawPhone
                            }

                            if (cleanPhone.isNotEmpty()) {
                                val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(messageText)}"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, messageText)
                                }
                                context.startActivity(Intent.createChooser(intent, "Kirim Pengingat"))
                            }
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MasukGreen)
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("WhatsApp", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
