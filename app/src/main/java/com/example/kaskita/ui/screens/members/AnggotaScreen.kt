package com.example.kaskita.ui.screens.members

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kaskita.data.model.Anggota
import com.example.kaskita.ui.theme.*
import com.example.kaskita.ui.viewmodel.KasKitaViewModel
import com.example.kaskita.util.DateUtils

@Composable
fun AnggotaScreen(viewModel: KasKitaViewModel) {
    val members by viewModel.members.collectAsState()
    val context = LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedMemberForEdit by remember { mutableStateOf<Anggota?>(null) }

    val filteredMembers = members.filter {
        it.nama.contains(searchQuery, ignoreCase = true) ||
                (it.noHp?.contains(searchQuery) == true) ||
                (it.catatan?.contains(searchQuery, ignoreCase = true) == true)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PineGreen,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("anggota_fab_add")
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = "Tambah Anggota")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Cari nama, no HP, atau catatan...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Hapus")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("anggota_search_input"),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Daftar Anggota",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${filteredMembers.size} dari ${members.size} orang",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (filteredMembers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (searchQuery.isEmpty()) "Belum ada anggota" else "Tidak ada anggota yang cocok",
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
                    items(filteredMembers, key = { it.id }) { member ->
                        AnggotaItemCard(
                            member = member,
                            onClick = { selectedMemberForEdit = member },
                            onWhatsAppClick = {
                                member.noHp?.let { phone ->
                                    val clean = phone.replace("[^0-9]".toRegex(), "")
                                    val target = if (clean.startsWith("0")) "62" + clean.substring(1) else clean
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=$target"))
                                    context.startActivity(intent)
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AnggotaFormDialog(
            initialMember = null,
            onDismiss = { showAddDialog = false },
            onSave = { nama, noHp, catatan, tanggal ->
                viewModel.addMember(nama, noHp, catatan, tanggal)
                showAddDialog = false
            }
        )
    }

    selectedMemberForEdit?.let { member ->
        AnggotaFormDialog(
            initialMember = member,
            onDismiss = { selectedMemberForEdit = null },
            onSave = { nama, noHp, catatan, tanggal ->
                viewModel.updateMember(
                    member.copy(
                        nama = nama,
                        noHp = noHp,
                        catatan = catatan,
                        tanggalGabung = tanggal
                    )
                )
                selectedMemberForEdit = null
            },
            onDelete = {
                viewModel.deleteMember(member.id)
                selectedMemberForEdit = null
            },
            onToggleAktif = {
                viewModel.updateMember(member.copy(aktif = !member.aktif))
                selectedMemberForEdit = null
            }
        )
    }
}

@Composable
private fun AnggotaItemCard(
    member: Anggota,
    onClick: () -> Unit,
    onWhatsAppClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("anggota_item_${member.id}"),
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            if (member.aktif) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = member.nama.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = if (member.aktif) PineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = member.nama,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!member.aktif) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("Nonaktif", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (!member.noHp.isNullOrBlank()) {
                        Text(
                            text = member.noHp,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!member.catatan.isNullOrBlank()) {
                        Text(
                            text = member.catatan,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (!member.noHp.isNullOrBlank()) {
                IconButton(
                    onClick = onWhatsAppClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Chat,
                        contentDescription = "WhatsApp",
                        tint = MasukGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AnggotaFormDialog(
    initialMember: Anggota?,
    onDismiss: () -> Unit,
    onSave: (nama: String, noHp: String?, catatan: String?, tanggalGabung: String) -> Unit,
    onDelete: (() -> Unit)? = null,
    onToggleAktif: (() -> Unit)? = null
) {
    var nama by remember { mutableStateOf(initialMember?.nama ?: "") }
    var noHp by remember { mutableStateOf(initialMember?.noHp ?: "") }
    var catatan by remember { mutableStateOf(initialMember?.catatan ?: "") }
    var tanggalGabung by remember { mutableStateOf(initialMember?.tanggalGabung ?: DateUtils.todayStr()) }

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
                        text = if (initialMember == null) "Tambah Anggota" else "Edit Data Anggota",
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
                    label = { Text("Nama Lengkap") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("anggota_form_name_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = noHp,
                    onValueChange = { noHp = it },
                    label = { Text("No. HP / WhatsApp (opsional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("anggota_form_phone_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = catatan,
                    onValueChange = { catatan = it },
                    label = { Text("Alamat / Catatan (opsional)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("anggota_form_notes_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = tanggalGabung,
                    onValueChange = { tanggalGabung = it },
                    label = { Text("Tanggal Gabung (YYYY-MM-DD)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("anggota_form_date_input"),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onDelete != null) {
                        OutlinedButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = KeluarRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Hapus")
                        }
                    }

                    if (onToggleAktif != null && initialMember != null) {
                        OutlinedButton(
                            onClick = onToggleAktif,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (initialMember.aktif) "Nonaktifkan" else "Aktifkan", fontSize = 12.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (nama.isNotBlank()) {
                                onSave(nama, noHp.ifBlank { null }, catatan.ifBlank { null }, tanggalGabung)
                            }
                        },
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PineGreen)
                    ) {
                        Text("Simpan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
