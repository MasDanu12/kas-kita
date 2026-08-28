package com.example.kaskita.ui.screens.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.kaskita.data.model.TransaksiWithDetails
import com.example.kaskita.ui.theme.*
import com.example.kaskita.ui.viewmodel.KasKitaViewModel
import com.example.kaskita.util.CurrencyFormatter
import com.example.kaskita.util.DateUtils

@Composable
fun KasScreen(
    viewModel: KasKitaViewModel,
    onOpenAddDialog: (String) -> Unit
) {
    val transactions by viewModel.transactions.collectAsState()
    val filterType by viewModel.kasFilter.collectAsState()

    var showDeleteConfirmDialog by remember { mutableStateOf<TransaksiWithDetails?>(null) }

    val filteredList = if (filterType.isEmpty()) {
        transactions
    } else {
        transactions.filter { it.tipe == filterType }
    }

    val totalMasuk = filteredList.filter { it.tipe == "masuk" }.sumOf { it.jumlah }
    val totalKeluar = filteredList.filter { it.tipe == "keluar" }.sumOf { it.jumlah }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onOpenAddDialog("masuk") },
                containerColor = PineGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("kas_fab_add")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Transaksi")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = filterType == "",
                        onClick = { viewModel.setKasFilter("") },
                        label = { Text("Semua") },
                        modifier = Modifier.testTag("kas_filter_all")
                    )
                }
                item {
                    FilterChip(
                        selected = filterType == "masuk",
                        onClick = { viewModel.setKasFilter("masuk") },
                        label = { Text("Masuk") },
                        modifier = Modifier.testTag("kas_filter_masuk")
                    )
                }
                item {
                    FilterChip(
                        selected = filterType == "keluar",
                        onClick = { viewModel.setKasFilter("keluar") },
                        label = { Text("Keluar") },
                        modifier = Modifier.testTag("kas_filter_keluar")
                    )
                }
                item {
                    FilterChip(
                        selected = filterType == "transfer",
                        onClick = { viewModel.setKasFilter("transfer") },
                        label = { Text("Transfer") },
                        modifier = Modifier.testTag("kas_filter_transfer")
                    )
                }
                item {
                    FilterChip(
                        selected = filterType == "penyesuaian",
                        onClick = { viewModel.setKasFilter("penyesuaian") },
                        label = { Text("Penyesuaian") },
                        modifier = Modifier.testTag("kas_filter_adjustment")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-Summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${filteredList.size} transaksi",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "+${CurrencyFormatter.formatCompact(totalMasuk)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MasukGreen
                        )
                        Text(
                            text = "-${CurrencyFormatter.formatCompact(totalKeluar)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KeluarRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Belum ada riwayat transaksi",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        TransaksiItemCard(
                            trx = item,
                            onShowReceipt = { viewModel.showReceiptForTransaction(item.id) },
                            onDelete = { showDeleteConfirmDialog = item }
                        )
                    }
                }
            }
        }
    }

    showDeleteConfirmDialog?.let { trx ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Hapus Transaksi?") },
            text = {
                Text(
                    text = if (trx.sumber == "iuran") {
                        "Menghapus transaksi ini juga akan membatalkan alokasi iuran terkait."
                    } else {
                        "Apakah Anda yakin ingin menghapus transaksi ini dari buku kas?"
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(trx.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KeluarRed)
                ) {
                    Text("Hapus")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Batal")
                }
            }
        )
    }
}

@Composable
private fun TransaksiItemCard(
    trx: TransaksiWithDetails,
    onShowReceipt: () -> Unit,
    onDelete: () -> Unit
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
        "transfer" -> "Transfer Akun"
        else -> "Penyesuaian Saldo"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaksi_item_${trx.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
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
                            .size(38.dp)
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
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (trx.sumber == "iuran") {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .background(GoldContainer, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Text("Iuran", fontSize = 9.sp, color = GoldAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Text(
                            text = DateUtils.formatDisplayDate(trx.tanggal),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "$sign${CurrencyFormatter.format(Math.abs(trx.jumlah))}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
            }

            if (!trx.catatan.isNullOrBlank() || trx.anggotaNama != null || trx.akunNama != null) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        trx.anggotaNama?.let {
                            Text(
                                text = "👤 $it",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (trx.tipe == "transfer") {
                            Text(
                                text = "💳 ${trx.akunNama ?: "-"} ➔ ${trx.akunTujuanNama ?: "-"}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            trx.akunNama?.let {
                                Text(
                                    text = "💳 $it (${trx.metode ?: "Tunai"})",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (!trx.catatan.isNullOrBlank()) {
                            Text(
                                text = "📝 ${trx.catatan}",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onShowReceipt,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Receipt,
                                contentDescription = "Struk",
                                tint = PineGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Hapus",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransaksiDialog(
    initialType: String = "masuk",
    viewModel: KasKitaViewModel,
    onDismiss: () -> Unit
) {
    val categories by viewModel.categories.collectAsState()
    val accounts by viewModel.accountsWithSaldo.collectAsState()
    val members by viewModel.members.collectAsState()

    var tipe by remember { mutableStateOf(initialType) }
    var jumlahStr by remember { mutableStateOf("") }
    var selectedKategori by remember {
        val cat = categories.firstOrNull { it.tipe == initialType }?.nama
        mutableStateOf(cat ?: "")
    }
    var selectedAkunId by remember { mutableStateOf(accounts.firstOrNull()?.id ?: "") }
    var selectedAkunTujuanId by remember { mutableStateOf(accounts.getOrNull(1)?.id ?: "") }
    var selectedMetode by remember { mutableStateOf("Tunai") }
    var selectedAnggotaId by remember { mutableStateOf<String?>(null) }
    var catatan by remember { mutableStateOf("") }
    var tanggal by remember { mutableStateOf(DateUtils.todayStr()) }

    var expandedKategori by remember { mutableStateOf(false) }
    var expandedAkun by remember { mutableStateOf(false) }
    var expandedAkunTujuan by remember { mutableStateOf(false) }
    var expandedMetode by remember { mutableStateOf(false) }
    var expandedAnggota by remember { mutableStateOf(false) }

    val relevantCategories = categories.filter { it.tipe == tipe }
    val paymentMethods = listOf("Tunai", "Transfer Bank", "E-Wallet", "Lainnya")

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
                            text = "Catat Transaksi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = PineGreen
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Tutup")
                        }
                    }
                }

                // Type Segmented selector
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                            .padding(4.dp)
                    ) {
                        listOf(
                            Pair("masuk", "Masuk"),
                            Pair("keluar", "Keluar"),
                            Pair("transfer", "Transfer"),
                            Pair("penyesuaian", "Penyesuaian")
                        ).forEach { (key, label) ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (tipe == key) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer)
                                    .clickable {
                                        tipe = key
                                        selectedKategori = categories.firstOrNull { it.tipe == key }?.nama ?: ""
                                    }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (tipe == key) FontWeight.Bold else FontWeight.Normal,
                                    color = if (tipe == key) PineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Nominal
                item {
                    OutlinedTextField(
                        value = jumlahStr,
                        onValueChange = { jumlahStr = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Jumlah Nominal (Rp)") },
                        prefix = { Text("Rp ") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("transaksi_amount_input"),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // Kategori (For Masuk / Keluar)
                if (tipe == "masuk" || tipe == "keluar") {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = expandedKategori,
                            onExpandedChange = { expandedKategori = !expandedKategori }
                        ) {
                            OutlinedTextField(
                                value = selectedKategori,
                                onValueChange = { selectedKategori = it },
                                label = { Text("Kategori") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedKategori) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedKategori,
                                onDismissRequest = { expandedKategori = false }
                            ) {
                                relevantCategories.forEach { kat ->
                                    DropdownMenuItem(
                                        text = { Text(kat.nama) },
                                        onClick = {
                                            selectedKategori = kat.nama
                                            expandedKategori = false
                                        }
                                    )
                                }
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
                            label = { Text(if (tipe == "transfer") "Dari Akun" else "Akun Kas") },
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

                // Akun Tujuan (Transfer only)
                if (tipe == "transfer") {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = expandedAkunTujuan,
                            onExpandedChange = { expandedAkunTujuan = !expandedAkunTujuan }
                        ) {
                            val accTujuanName = accounts.firstOrNull { it.id == selectedAkunTujuanId }?.nama ?: "Pilih Akun Tujuan"
                            OutlinedTextField(
                                value = accTujuanName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Ke Akun Tujuan") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAkunTujuan) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedAkunTujuan,
                                onDismissRequest = { expandedAkunTujuan = false }
                            ) {
                                accounts.forEach { acc ->
                                    DropdownMenuItem(
                                        text = { Text("${acc.nama} (${CurrencyFormatter.formatCompact(acc.saldo)})") },
                                        onClick = {
                                            selectedAkunTujuanId = acc.id
                                            expandedAkunTujuan = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Metode Pembayaran
                if (tipe != "transfer") {
                    item {
                        ExposedDropdownMenuBox(
                            expanded = expandedMetode,
                            onExpandedChange = { expandedMetode = !expandedMetode }
                        ) {
                            OutlinedTextField(
                                value = selectedMetode,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Metode") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedMetode) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(),
                                shape = RoundedCornerShape(10.dp)
                            )
                            ExposedDropdownMenu(
                                expanded = expandedMetode,
                                onDismissRequest = { expandedMetode = false }
                            ) {
                                paymentMethods.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text(m) },
                                        onClick = {
                                            selectedMetode = m
                                            expandedMetode = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Tanggal
                item {
                    OutlinedTextField(
                        value = tanggal,
                        onValueChange = { tanggal = it },
                        label = { Text("Tanggal (YYYY-MM-DD)") },
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
                        label = { Text("Catatan / Keterangan (opsional)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )
                }

                // Submit Button
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val amount = jumlahStr.toDoubleOrNull() ?: 0.0
                            viewModel.addTransaction(
                                tipe = tipe,
                                kategori = if (tipe == "masuk" || tipe == "keluar") selectedKategori.ifBlank { null } else null,
                                jumlah = amount,
                                catatan = catatan.ifBlank { null },
                                metode = selectedMetode,
                                akunId = selectedAkunId.ifBlank { null },
                                akunTujuanId = if (tipe == "transfer") selectedAkunTujuanId.ifBlank { null } else null,
                                tanggal = tanggal,
                                anggotaId = selectedAnggotaId
                            )
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("transaksi_dialog_submit_btn"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (tipe) {
                                "keluar" -> KeluarRed
                                else -> PineGreen
                            }
                        )
                    ) {
                        Text("Simpan Transaksi", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
