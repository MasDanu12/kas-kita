package com.example.kaskita.ui.screens.dues

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kaskita.data.model.Anggota
import com.example.kaskita.data.model.MemberArrears
import com.example.kaskita.data.model.MemberDuesStatus
import com.example.kaskita.ui.components.WhatsAppReminderDialog
import com.example.kaskita.ui.theme.*
import com.example.kaskita.ui.viewmodel.KasKitaViewModel
import com.example.kaskita.util.CurrencyFormatter
import com.example.kaskita.util.DateUtils
import kotlinx.coroutines.launch

@Composable
fun IuranScreen(viewModel: KasKitaViewModel) {
    val duesOverview by viewModel.duesOverview.collectAsState()
    val duesSettings by viewModel.duesSettings.collectAsState()
    val selectedPeriode by viewModel.iuranSelectedPeriode.collectAsState()
    val currentOrg by viewModel.currentOrg.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var showPayDialog by remember { mutableStateOf(false) }
    var selectedMemberForPayment by remember { mutableStateOf<Anggota?>(null) }
    var showArrearsDialog by remember { mutableStateOf(false) }
    var arrearsList by remember { mutableStateOf<List<MemberArrears>>(emptyList()) }
    var activeReminderMember by remember { mutableStateOf<MemberArrears?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedMemberForPayment = null
                    showPayDialog = true
                },
                containerColor = PineGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("iuran_fab_pay")
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = "Bayar Iuran")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Period Selector Header
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
                    IconButton(
                        onClick = {
                            viewModel.setIuranPeriode(DateUtils.periodAdd(selectedPeriode, -1))
                        }
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Bulan Sebelumnya")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = DateUtils.formatMonthYear(selectedPeriode),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PineGreen
                        )
                        Text(
                            text = "Periode $selectedPeriode",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.setIuranPeriode(DateUtils.periodAdd(selectedPeriode, 1))
                        }
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Bulan Berikutnya")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Settings & Arrears action bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { showSettingsDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${duesSettings?.namaIuran ?: "Iuran"}: ${CurrencyFormatter.format(duesSettings?.nominal ?: 0.0)}/bln",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            arrearsList = viewModel.getArrearsList()
                            showArrearsDialog = true
                        }
                    },
                    modifier = Modifier.testTag("iuran_arrears_report_btn")
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Laporan Tunggakan", fontSize = 12.sp, color = PineGreen, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Summary Stats Grid
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DuesStatSmall(title = "Lunas", value = "${duesOverview?.lunas ?: 0}", color = MasukGreen, modifier = Modifier.weight(1f))
                        DuesStatSmall(title = "Sebagian", value = "${duesOverview?.sebagian ?: 0}", color = GoldAccent, modifier = Modifier.weight(1f))
                        DuesStatSmall(title = "Belum Bayar", value = "${duesOverview?.menunggak ?: 0}", color = KeluarRed, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Terkumpul Bulan Ini", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyFormatter.format(duesOverview?.terkumpul ?: 0.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MasukGreen)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Tunggakan Periode Ini", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(CurrencyFormatter.format(duesOverview?.tunggakan ?: 0.0), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = KeluarRed)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Member Status List
            Text(
                text = "Status Pembayaran Anggota",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            val statusList = duesOverview?.statusList ?: emptyList()
            if (statusList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Belum ada anggota aktif di organisasi ini",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(statusList, key = { it.anggotaId }) { item ->
                        MemberDuesItemCard(
                            status = item,
                            onPayClick = {
                                val m = Anggota(
                                    id = item.anggotaId,
                                    organizationId = "",
                                    nama = item.nama,
                                    noHp = item.noHp,
                                    tanggalGabung = ""
                                )
                                selectedMemberForPayment = m
                                showPayDialog = true
                            },
                            onReminderClick = {
                                activeReminderMember = MemberArrears(
                                    anggotaId = item.anggotaId,
                                    nama = item.nama,
                                    noHp = item.noHp,
                                    totalTunggakan = maxOf(0.0, item.wajib - item.dibayar)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        IuranSettingsDialog(
            currentSettings = duesSettings,
            onDismiss = { showSettingsDialog = false },
            onSave = { nama, nominal, tanggal ->
                viewModel.updateDuesSettings(nama, nominal, tanggal)
                showSettingsDialog = false
            }
        )
    }

    if (showPayDialog) {
        IuranBayarDialog(
            preselectedMember = selectedMemberForPayment,
            viewModel = viewModel,
            onDismiss = {
                showPayDialog = false
                selectedMemberForPayment = null
            }
        )
    }

    if (showArrearsDialog) {
        LaporanTunggakanDialog(
            arrearsList = arrearsList,
            orgName = currentOrg?.nama ?: "Kas Kita",
            periode = selectedPeriode,
            onDismiss = { showArrearsDialog = false },
            onRemindMember = { arr ->
                activeReminderMember = arr
            }
        )
    }

    activeReminderMember?.let { arr ->
        WhatsAppReminderDialog(
            arrears = arr,
            orgName = currentOrg?.nama ?: "Kas Kita",
            periode = selectedPeriode,
            onDismiss = { activeReminderMember = null }
        )
    }
}

@Composable
private fun DuesStatSmall(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MemberDuesItemCard(
    status: MemberDuesStatus,
    onPayClick: () -> Unit,
    onReminderClick: () -> Unit
) {
    val (badgeBg, badgeColor, badgeText) = when (status.status) {
        "lunas" -> Triple(MasukGreenLight, MasukGreen, "Lunas")
        "sebagian" -> Triple(GoldContainer, GoldAccent, "Sebagian (${CurrencyFormatter.formatCompact(status.dibayar)})")
        "belum_bayar" -> Triple(KeluarRedLight, KeluarRed, "Belum Bayar")
        else -> Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, "Tidak Dikenakan")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("dues_member_item_${status.anggotaId}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.nama,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(badgeBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(badgeText, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = badgeColor)
                    }

                    if (status.lunasSampai != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lunas s/d ${status.lunasSampai}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (status.status == "belum_bayar" || status.status == "sebagian") {
                    IconButton(
                        onClick = onReminderClick,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Chat,
                            contentDescription = "Ingatkan",
                            tint = MasukGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Button(
                    onClick = onPayClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PineGreen),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text("Bayar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun IuranSettingsDialog(
    currentSettings: com.example.kaskita.data.model.IuranSettings?,
    onDismiss: () -> Unit,
    onSave: (nama: String, nominal: Double, tanggalMulai: String) -> Unit
) {
    var nama by remember { mutableStateOf(currentSettings?.namaIuran ?: "Iuran Bulanan") }
    var nominalStr by remember { mutableStateOf((currentSettings?.nominal?.toLong() ?: 25000L).toString()) }
    var tanggalMulai by remember { mutableStateOf(currentSettings?.tanggalMulai ?: DateUtils.todayStr()) }

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
                        text = "Pengaturan Iuran",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = PineGreen
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = nama,
                    onValueChange = { nama = it },
                    label = { Text("Nama Iuran") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = nominalStr,
                    onValueChange = { nominalStr = it.filter { c -> c.isDigit() } },
                    label = { Text("Nominal per Bulan (Rp)") },
                    prefix = { Text("Rp ") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tanggalMulai,
                    onValueChange = { tanggalMulai = it },
                    label = { Text("Mulai Diberlakukan (YYYY-MM-DD)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = {
                        val nom = nominalStr.toDoubleOrNull() ?: 0.0
                        onSave(nama, nom, tanggalMulai)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PineGreen)
                ) {
                    Text("Simpan Pengaturan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IuranBayarDialog(
    preselectedMember: Anggota?,
    viewModel: KasKitaViewModel,
    onDismiss: () -> Unit
) {
    val members by viewModel.members.collectAsState()
    val accounts by viewModel.accountsWithSaldo.collectAsState()
    val settings by viewModel.duesSettings.collectAsState()

    var selectedMemberId by remember { mutableStateOf(preselectedMember?.id ?: members.firstOrNull()?.id ?: "") }
    var jumlahStr by remember { mutableStateOf((settings?.nominal?.toLong() ?: 25000L).toString()) }
    var selectedAkunId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var tanggal by remember { mutableStateOf(DateUtils.todayStr()) }
    var catatan by remember { mutableStateOf("") }

    var expandedMember by remember { mutableStateOf(false) }
    var expandedAkun by remember { mutableStateOf(false) }

    val monthlyNominal = settings?.nominal ?: 25000.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Bayar Iuran",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PineGreen
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                // Member Dropdown
                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedMember,
                        onExpandedChange = { expandedMember = !expandedMember }
                    ) {
                        val memberName = members.firstOrNull { it.id == selectedMemberId }?.nama ?: "Pilih Anggota"
                        OutlinedTextField(
                            value = memberName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Pilih Anggota") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMember) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedMember,
                            onDismissRequest = { expandedMember = false }
                        ) {
                            members.filter { it.aktif }.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text(m.nama) },
                                    onClick = {
                                        selectedMemberId = m.id
                                        expandedMember = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Nominal Input
                item {
                    OutlinedTextField(
                        value = jumlahStr,
                        onValueChange = { jumlahStr = it.filter { c -> c.isDigit() } },
                        label = { Text("Jumlah Bayar (Rp)") },
                        prefix = { Text("Rp ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("iuran_amount_input"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // Quick Chips for Multi-Months
                if (monthlyNominal > 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                Pair("1 Bln", monthlyNominal * 1),
                                Pair("2 Bln", monthlyNominal * 2),
                                Pair("3 Bln", monthlyNominal * 3),
                                Pair("6 Bln", monthlyNominal * 6),
                                Pair("1 Thn", monthlyNominal * 12)
                            ).forEach { (label, amt) ->
                                SuggestionChip(
                                    onClick = { jumlahStr = amt.toLong().toString() },
                                    label = { Text(label, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }

                // Akun Kas
                item {
                    ExposedDropdownMenuBox(
                        expanded = expandedAkun,
                        onExpandedChange = { expandedAkun = !expandedAkun }
                    ) {
                        val activeAccName = accounts.firstOrNull { it.id == selectedAkunId }?.nama ?: "Pilih Akun"
                        OutlinedTextField(
                            value = activeAccName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Masuk ke Akun Kas") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAkun) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = expandedAkun,
                            onDismissRequest = { expandedAkun = false }
                        ) {
                            accounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text("${acc.nama} (${CurrencyFormatter.formatCompact(acc.saldo)})") },
                                    onClick = {
                                        selectedAkunId = acc.id
                                        expandedAkun = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Tanggal
                item {
                    OutlinedTextField(
                        value = tanggal,
                        onValueChange = { tanggal = it },
                        label = { Text("Tanggal Bayar (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // Catatan
                item {
                    OutlinedTextField(
                        value = catatan,
                        onValueChange = { catatan = it },
                        label = { Text("Catatan (opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // Submit
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val amt = jumlahStr.toDoubleOrNull() ?: 0.0
                            if (selectedMemberId.isNotBlank() && amt > 0) {
                                viewModel.payDues(
                                    anggotaId = selectedMemberId,
                                    jumlah = amt,
                                    tanggal = tanggal,
                                    akunId = selectedAkunId.ifBlank { null },
                                    catatan = catatan.ifBlank { null }
                                )
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("iuran_submit_payment_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PineGreen)
                    ) {
                        Text("Catat Pembayaran", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun LaporanTunggakanDialog(
    arrearsList: List<MemberArrears>,
    orgName: String,
    periode: String,
    onDismiss: () -> Unit,
    onRemindMember: (MemberArrears) -> Unit
) {
    val totalTunggakan = arrearsList.sumOf { it.totalTunggakan }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
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
                    Column {
                        Text(
                            text = "Laporan Tunggakan",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PineGreen
                        )
                        Text(
                            text = "Total: ${CurrencyFormatter.format(totalTunggakan)} (${arrearsList.size} anggota)",
                            fontSize = 12.sp,
                            color = KeluarRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (arrearsList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Semua anggota sudah lunas! 🎉", fontSize = 14.sp, color = MasukGreen)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(arrearsList) { arr ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(arr.nama, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            CurrencyFormatter.format(arr.totalTunggakan),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = KeluarRed
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            onDismiss()
                                            onRemindMember(arr)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MasukGreen),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Ingatkan", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Tutup")
                }
            }
        }
    }
}
