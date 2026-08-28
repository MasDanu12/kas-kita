package com.example.kaskita

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kaskita.ui.components.ReceiptDialog
import com.example.kaskita.ui.screens.auth.AuthScreen
import com.example.kaskita.ui.screens.auth.OrgSelectScreen
import com.example.kaskita.ui.screens.cash.KasScreen
import com.example.kaskita.ui.screens.cash.TransaksiDialog
import com.example.kaskita.ui.screens.dues.IuranBayarDialog
import com.example.kaskita.ui.screens.dues.IuranScreen
import com.example.kaskita.ui.screens.home.HomeScreen
import com.example.kaskita.ui.screens.members.AnggotaScreen
import com.example.kaskita.ui.screens.profile.ProfilScreen
import com.example.kaskita.ui.screens.reports.LaporanScreen
import com.example.kaskita.ui.theme.KasKitaTheme
import com.example.kaskita.ui.theme.PineGreen
import com.example.kaskita.ui.viewmodel.AppNavState
import com.example.kaskita.ui.viewmodel.KasKitaViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: KasKitaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val darkModePref by viewModel.isDarkMode.collectAsState()
            val isDark = darkModePref ?: isSystemInDarkTheme()

            KasKitaTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppContent(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContent(viewModel: KasKitaViewModel) {
    val navState by viewModel.navState.collectAsState()
    val currentOrg by viewModel.currentOrg.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val activeReceipt by viewModel.activeReceipt.collectAsState()
    val context = LocalContext.current

    var showTransactionDialog by remember { mutableStateOf(false) }
    var transactionDialogType by remember { mutableStateOf("masuk") }
    var showPayDuesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    when (navState) {
        is AppNavState.Auth -> {
            AuthScreen(viewModel = viewModel)
        }
        is AppNavState.OrgSelect -> {
            OrgSelectScreen(viewModel = viewModel)
        }
        is AppNavState.MainApp -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = currentOrg?.nama ?: "Kas Kita",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = when (selectedTab) {
                                        "beranda" -> "Beranda"
                                        "kas" -> "Buku Kas"
                                        "iuran" -> "Iuran Anggota"
                                        "laporan" -> "Laporan Keuangan"
                                        "anggota" -> "Daftar Anggota"
                                        else -> "Profil & Pengaturan"
                                    },
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.selectTab("profil") },
                                modifier = Modifier.testTag("topbar_profile_btn")
                            ) {
                                Icon(
                                    imageVector = if (selectedTab == "profil") Icons.Filled.Person else Icons.Outlined.Person,
                                    contentDescription = "Profil",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = PineGreen,
                            titleContentColor = Color.White,
                            actionIconContentColor = Color.White
                        )
                    )
                },
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        NavigationBarItem(
                            selected = selectedTab == "beranda",
                            onClick = { viewModel.selectTab("beranda") },
                            icon = {
                                Icon(
                                    if (selectedTab == "beranda") Icons.Filled.Home else Icons.Outlined.Home,
                                    contentDescription = "Beranda"
                                )
                            },
                            label = { Text("Beranda", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_beranda")
                        )
                        NavigationBarItem(
                            selected = selectedTab == "kas",
                            onClick = { viewModel.selectTab("kas") },
                            icon = {
                                Icon(
                                    if (selectedTab == "kas") Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = "Buku Kas"
                                )
                            },
                            label = { Text("Buku Kas", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_kas")
                        )
                        NavigationBarItem(
                            selected = selectedTab == "iuran",
                            onClick = { viewModel.selectTab("iuran") },
                            icon = {
                                Icon(
                                    if (selectedTab == "iuran") Icons.Filled.CreditCard else Icons.Outlined.CreditCard,
                                    contentDescription = "Iuran"
                                )
                            },
                            label = { Text("Iuran", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_iuran")
                        )
                        NavigationBarItem(
                            selected = selectedTab == "laporan",
                            onClick = { viewModel.selectTab("laporan") },
                            icon = {
                                Icon(
                                    if (selectedTab == "laporan") Icons.Filled.BarChart else Icons.Outlined.BarChart,
                                    contentDescription = "Laporan"
                                )
                            },
                            label = { Text("Laporan", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_laporan")
                        )
                        NavigationBarItem(
                            selected = selectedTab == "anggota",
                            onClick = { viewModel.selectTab("anggota") },
                            icon = {
                                Icon(
                                    if (selectedTab == "anggota") Icons.Filled.People else Icons.Outlined.People,
                                    contentDescription = "Anggota"
                                )
                            },
                            label = { Text("Anggota", fontSize = 11.sp) },
                            modifier = Modifier.testTag("nav_item_anggota")
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    when (selectedTab) {
                        "beranda" -> HomeScreen(
                            viewModel = viewModel,
                            onOpenTransactionDialog = { type ->
                                transactionDialogType = type
                                showTransactionDialog = true
                            },
                            onOpenPayDuesDialog = {
                                showPayDuesDialog = true
                            }
                        )
                        "kas" -> KasScreen(
                            viewModel = viewModel,
                            onOpenAddDialog = { type ->
                                transactionDialogType = type
                                showTransactionDialog = true
                            }
                        )
                        "iuran" -> IuranScreen(viewModel = viewModel)
                        "laporan" -> LaporanScreen(viewModel = viewModel)
                        "anggota" -> AnggotaScreen(viewModel = viewModel)
                        "profil" -> ProfilScreen(viewModel = viewModel)
                    }
                }
            }

            if (showTransactionDialog) {
                TransaksiDialog(
                    initialType = transactionDialogType,
                    viewModel = viewModel,
                    onDismiss = { showTransactionDialog = false }
                )
            }

            if (showPayDuesDialog) {
                IuranBayarDialog(
                    preselectedMember = null,
                    viewModel = viewModel,
                    onDismiss = { showPayDuesDialog = false }
                )
            }

            activeReceipt?.let { receipt ->
                ReceiptDialog(
                    receipt = receipt,
                    onDismiss = { viewModel.clearReceipt() }
                )
            }
        }
    }
}
