package com.example.kaskita.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kaskita.KasKitaApplication
import com.example.kaskita.data.model.*
import com.example.kaskita.util.DateUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class AppNavState {
    object Auth : AppNavState()
    object OrgSelect : AppNavState()
    object MainApp : AppNavState()
}

class KasKitaViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as KasKitaApplication).repository

    // ---- Auth & Active Scope ----
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _currentOrg = MutableStateFlow<Organization?>(null)
    val currentOrg: StateFlow<Organization?> = _currentOrg.asStateFlow()

    private val _navState = MutableStateFlow<AppNavState>(AppNavState.Auth)
    val navState: StateFlow<AppNavState> = _navState.asStateFlow()

    private val _userOrgs = MutableStateFlow<List<Organization>>(emptyList())
    val userOrgs: StateFlow<List<Organization>> = _userOrgs.asStateFlow()

    // ---- Active Tab & Filter State ----
    private val _selectedTab = MutableStateFlow("beranda")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _kasFilter = MutableStateFlow("") // "", "masuk", "keluar", "transfer", "penyesuaian"
    val kasFilter: StateFlow<String> = _kasFilter.asStateFlow()

    private val _iuranSelectedPeriode = MutableStateFlow(DateUtils.currentPeriode())
    val iuranSelectedPeriode: StateFlow<String> = _iuranSelectedPeriode.asStateFlow()

    private val _laporanSelectedBulan = MutableStateFlow(DateUtils.currentPeriode())
    val laporanSelectedBulan: StateFlow<String> = _laporanSelectedBulan.asStateFlow()

    private val _laporanSelectedTahun = MutableStateFlow(DateUtils.currentYear())
    val laporanSelectedTahun: StateFlow<String> = _laporanSelectedTahun.asStateFlow()

    private val _laporanTab = MutableStateFlow("bulanan") // "bulanan", "tahunan"
    val laporanTab: StateFlow<String> = _laporanTab.asStateFlow()

    private val _isDarkMode = MutableStateFlow<Boolean?>(null) // null = system
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    // ---- Organization Data State ----
    private val _accountsWithSaldo = MutableStateFlow<List<AkunWithSaldo>>(emptyList())
    val accountsWithSaldo: StateFlow<List<AkunWithSaldo>> = _accountsWithSaldo.asStateFlow()

    private val _members = MutableStateFlow<List<Anggota>>(emptyList())
    val members: StateFlow<List<Anggota>> = _members.asStateFlow()

    private val _categories = MutableStateFlow<List<Kategori>>(emptyList())
    val categories: StateFlow<List<Kategori>> = _categories.asStateFlow()

    private val _transactions = MutableStateFlow<List<TransaksiWithDetails>>(emptyList())
    val transactions: StateFlow<List<TransaksiWithDetails>> = _transactions.asStateFlow()

    private val _duesSettings = MutableStateFlow<IuranSettings?>(null)
    val duesSettings: StateFlow<IuranSettings?> = _duesSettings.asStateFlow()

    private val _duesOverview = MutableStateFlow<DuesOverview?>(null)
    val duesOverview: StateFlow<DuesOverview?> = _duesOverview.asStateFlow()

    private val _monthlyReport = MutableStateFlow<MonthlyReportData?>(null)
    val monthlyReport: StateFlow<MonthlyReportData?> = _monthlyReport.asStateFlow()

    private val _annualReport = MutableStateFlow<AnnualReportData?>(null)
    val annualReport: StateFlow<AnnualReportData?> = _annualReport.asStateFlow()

    private val _activeReceipt = MutableStateFlow<ReceiptData?>(null)
    val activeReceipt: StateFlow<ReceiptData?> = _activeReceipt.asStateFlow()

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage.asSharedFlow()

    init {
        // Automatically check if there is an existing user to streamline start
        viewModelScope.launch {
            val defaultUser = repository.getFirstUser()
            if (defaultUser != null) {
                _currentUser.value = defaultUser
                loadUserOrganizations(defaultUser.id, autoSelectFirst = true)
            } else {
                _navState.value = AppNavState.Auth
            }
        }
    }

    private fun postToast(msg: String) {
        viewModelScope.launch { _toastMessage.emit(msg) }
    }

    // ---- Auth Actions ----
    fun login(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank() || password.isBlank()) {
                postToast("Email dan password wajib diisi")
                return@launch
            }
            val user = repository.getUserByEmail(email.trim().lowercase())
            if (user == null) {
                // Auto create demo user if not exists for easy onboarding
                val newUser = repository.registerUser(email, email.substringBefore("@").replace(".", " ").capitalize(), password)
                _currentUser.value = newUser
                postToast("Akun baru dibuat dan berhasil masuk")
                loadUserOrganizations(newUser.id, autoSelectFirst = false)
            } else {
                _currentUser.value = user
                postToast("Berhasil masuk")
                loadUserOrganizations(user.id, autoSelectFirst = true)
            }
        }
    }

    fun register(nama: String, email: String, password: String) {
        viewModelScope.launch {
            if (nama.isBlank() || email.isBlank() || password.isBlank()) {
                postToast("Semua data pendaftaran wajib diisi")
                return@launch
            }
            val existing = repository.getUserByEmail(email.trim().lowercase())
            if (existing != null) {
                postToast("Email sudah terdaftar")
                return@launch
            }
            val newUser = repository.registerUser(email, nama, password)
            _currentUser.value = newUser
            postToast("Pendaftaran berhasil!")
            loadUserOrganizations(newUser.id, autoSelectFirst = false)
        }
    }

    fun logout() {
        _currentUser.value = null
        _currentOrg.value = null
        _navState.value = AppNavState.Auth
        postToast("Berhasil keluar akun")
    }

    fun updateProfileName(nama: String) {
        val user = _currentUser.value ?: return
        if (nama.isBlank()) {
            postToast("Nama tidak boleh kosong")
            return
        }
        viewModelScope.launch {
            val updated = user.copy(nama = nama.trim())
            repository.updateUser(updated)
            _currentUser.value = updated
            postToast("Profil berhasil diperbarui")
        }
    }

    // ---- Organization Actions ----
    fun loadUserOrganizations(userId: String, autoSelectFirst: Boolean = false) {
        viewModelScope.launch {
            val orgs = repository.getOrganizationsForUserOnce(userId)
            _userOrgs.value = orgs
            if (orgs.isNotEmpty() && autoSelectFirst) {
                selectOrganization(orgs.first())
            } else if (orgs.isNotEmpty()) {
                _navState.value = AppNavState.OrgSelect
            } else {
                _navState.value = AppNavState.OrgSelect
            }
        }
    }

    fun selectOrganization(org: Organization) {
        _currentOrg.value = org
        _navState.value = AppNavState.MainApp
        _selectedTab.value = "beranda"
        refreshOrgData()
    }

    fun createOrganization(nama: String) {
        val user = _currentUser.value ?: return
        if (nama.isBlank()) {
            postToast("Nama organisasi wajib diisi")
            return
        }
        viewModelScope.launch {
            val newOrg = repository.createOrganization(user.id, nama)
            postToast("Organisasi \"${newOrg.nama}\" berhasil dibuat!")
            loadUserOrganizations(user.id, autoSelectFirst = false)
            selectOrganization(newOrg)
        }
    }

    fun joinOrganization(inviteCode: String) {
        val user = _currentUser.value ?: return
        if (inviteCode.isBlank()) {
            postToast("Kode undangan wajib diisi")
            return
        }
        viewModelScope.launch {
            val res = repository.joinOrganization(user.id, inviteCode)
            res.onSuccess { org ->
                postToast("Berhasil bergabung dengan ${org.nama}!")
                loadUserOrganizations(user.id, autoSelectFirst = false)
                selectOrganization(org)
            }.onFailure { err ->
                postToast(err.message ?: "Gagal bergabung organisasi")
            }
        }
    }

    fun openSwitchOrg() {
        _currentUser.value?.let { user ->
            loadUserOrganizations(user.id, autoSelectFirst = false)
            _navState.value = AppNavState.OrgSelect
        }
    }

    fun setNavState(state: AppNavState) {
        _navState.value = state
    }

    // ---- Navigation & Filter State ----
    fun selectTab(tab: String) {
        _selectedTab.value = tab
        if (tab == "laporan") {
            refreshLaporan()
        } else if (tab == "iuran") {
            refreshDuesOverview()
        }
    }

    fun setKasFilter(filter: String) {
        _kasFilter.value = filter
    }

    fun setIuranPeriode(periode: String) {
        _iuranSelectedPeriode.value = periode
        refreshDuesOverview()
    }

    fun setLaporanBulan(bulan: String) {
        _laporanSelectedBulan.value = bulan
        refreshLaporan()
    }

    fun setLaporanTahun(tahun: String) {
        _laporanSelectedTahun.value = tahun
        refreshLaporan()
    }

    fun setLaporanTab(tab: String) {
        _laporanTab.value = tab
        refreshLaporan()
    }

    fun toggleDarkMode(enableDark: Boolean) {
        _isDarkMode.value = enableDark
    }

    fun clearReceipt() {
        _activeReceipt.value = null
    }

    fun showReceipt(receipt: ReceiptData) {
        _activeReceipt.value = receipt
    }

    fun showReceiptForTransaction(transaksiId: String) {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            val r = repository.getReceiptData(transaksiId, org.id)
            if (r != null) {
                _activeReceipt.value = r
            } else {
                postToast("Struk transaksi tidak ditemukan")
            }
        }
    }

    // ---- Data Refreshers ----
    fun refreshOrgData() {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            // Observe or fetch active accounts with balances
            _accountsWithSaldo.value = repository.getAkunWithSaldoList(org.id)
        }
        viewModelScope.launch {
            repository.getAnggotaList(org.id).collect { list ->
                _members.value = list
            }
        }
        viewModelScope.launch {
            repository.getKategoriList(org.id).collect { list ->
                _categories.value = list
            }
        }
        viewModelScope.launch {
            repository.getTransaksiWithDetails(org.id).collect { list ->
                _transactions.value = list
                // Also update accounts balance whenever transactions change
                _accountsWithSaldo.value = repository.getAkunWithSaldoList(org.id)
            }
        }
        viewModelScope.launch {
            repository.getIuranSettings(org.id).collect { settings ->
                _duesSettings.value = settings
            }
        }
        refreshDuesOverview()
        refreshLaporan()
    }

    fun refreshDuesOverview() {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            _duesOverview.value = repository.getDuesOverview(org.id, _iuranSelectedPeriode.value)
        }
    }

    fun refreshLaporan() {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            _monthlyReport.value = repository.getMonthlyReport(org.id, _laporanSelectedBulan.value)
            _annualReport.value = repository.getAnnualReport(org.id, _laporanSelectedTahun.value)
        }
    }

    // ---- Member CRUD ----
    fun addMember(nama: String, noHp: String?, catatan: String?, tanggalGabung: String) {
        val org = _currentOrg.value ?: return
        if (nama.isBlank()) {
            postToast("Nama anggota wajib diisi")
            return
        }
        viewModelScope.launch {
            val member = Anggota(
                organizationId = org.id,
                nama = nama.trim(),
                noHp = noHp?.trim()?.ifBlank { null },
                catatan = catatan?.trim()?.ifBlank { null },
                tanggalGabung = if (tanggalGabung.isBlank()) DateUtils.todayStr() else tanggalGabung
            )
            repository.insertAnggota(member)
            postToast("Anggota \"${member.nama}\" berhasil ditambahkan")
            refreshDuesOverview()
        }
    }

    fun updateMember(member: Anggota) {
        viewModelScope.launch {
            repository.updateAnggota(member)
            postToast("Data anggota diperbarui")
            refreshDuesOverview()
        }
    }

    fun deleteMember(memberId: String) {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            repository.deleteAnggota(memberId, org.id)
            postToast("Anggota dihapus")
            refreshDuesOverview()
        }
    }

    // ---- Transaction CRUD ----
    fun addTransaction(
        tipe: String,
        kategori: String?,
        jumlah: Double,
        catatan: String?,
        metode: String?,
        akunId: String?,
        akunTujuanId: String?,
        tanggal: String,
        anggotaId: String? = null
    ) {
        val org = _currentOrg.value ?: return
        val user = _currentUser.value ?: return

        if (tipe != "penyesuaian" && jumlah <= 0) {
            postToast("Jumlah harus lebih dari 0")
            return
        }
        if (tipe == "transfer") {
            if (akunId == null || akunTujuanId == null || akunId == akunTujuanId) {
                postToast("Pilih akun asal dan akun tujuan yang berbeda")
                return
            }
        }
        if (tipe == "penyesuaian" && catatan.isNullOrBlank()) {
            postToast("Keterangan wajib diisi untuk penyesuaian saldo")
            return
        }

        viewModelScope.launch {
            val trx = Transaksi(
                organizationId = org.id,
                tipe = tipe,
                sumber = "umum",
                kategori = kategori,
                jumlah = jumlah,
                catatan = catatan?.trim()?.ifBlank { null },
                metode = metode ?: "Tunai",
                akunId = akunId,
                akunTujuanId = akunTujuanId,
                anggotaId = anggotaId,
                tanggal = if (tanggal.isBlank()) DateUtils.todayStr() else tanggal,
                createdBy = user.id
            )
            repository.insertTransaksi(trx)
            postToast("Transaksi berhasil disimpan")
            _accountsWithSaldo.value = repository.getAkunWithSaldoList(org.id)
            refreshLaporan()

            if (tipe == "masuk" || tipe == "keluar") {
                val receipt = repository.getReceiptData(trx.id, org.id)
                receipt?.let { _activeReceipt.value = it }
            }
        }
    }

    fun deleteTransaction(transaksiId: String) {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            val res = repository.deleteTransaksi(transaksiId, org.id)
            res.onSuccess {
                postToast("Transaksi berhasil dihapus")
                _accountsWithSaldo.value = repository.getAkunWithSaldoList(org.id)
                refreshDuesOverview()
                refreshLaporan()
            }.onFailure { err ->
                postToast(err.message ?: "Gagal menghapus transaksi")
            }
        }
    }

    // ---- Dues Operations ----
    fun payDues(
        anggotaId: String,
        jumlah: Double,
        tanggal: String,
        akunId: String?,
        catatan: String?
    ) {
        val org = _currentOrg.value ?: return
        val user = _currentUser.value ?: return

        if (jumlah <= 0) {
            postToast("Jumlah pembayaran harus lebih dari 0")
            return
        }

        viewModelScope.launch {
            val res = repository.payDues(
                orgId = org.id,
                userId = user.id,
                anggotaId = anggotaId,
                jumlah = jumlah,
                tanggal = if (tanggal.isBlank()) DateUtils.todayStr() else tanggal,
                akunId = akunId,
                catatan = catatan?.trim()?.ifBlank { null }
            )

            res.onSuccess { receipt ->
                postToast("Pembayaran iuran berhasil dicatat!")
                _activeReceipt.value = receipt
                _accountsWithSaldo.value = repository.getAkunWithSaldoList(org.id)
                refreshDuesOverview()
                refreshLaporan()
            }.onFailure { err ->
                postToast(err.message ?: "Gagal mencatat pembayaran iuran")
            }
        }
    }

    fun updateDuesSettings(namaIuran: String, nominal: Double, tanggalMulai: String) {
        val org = _currentOrg.value ?: return
        if (nominal < 0) {
            postToast("Nominal iuran tidak boleh negatif")
            return
        }
        viewModelScope.launch {
            val updated = IuranSettings(
                organizationId = org.id,
                namaIuran = if (namaIuran.isBlank()) "Iuran Bulanan" else namaIuran.trim(),
                nominal = nominal,
                tanggalMulai = if (tanggalMulai.isBlank()) DateUtils.todayStr() else tanggalMulai
            )
            repository.updateIuranSettings(updated)
            postToast("Pengaturan iuran berhasil disimpan")
            refreshDuesOverview()
        }
    }

    suspend fun getArrearsList(): List<MemberArrears> {
        val org = _currentOrg.value ?: return emptyList()
        return repository.getMemberArrearsList(org.id, _iuranSelectedPeriode.value)
    }

    // ---- Account & Category Management ----
    fun addAccount(nama: String, saldoAwal: Double) {
        val org = _currentOrg.value ?: return
        if (nama.isBlank()) {
            postToast("Nama akun wajib diisi")
            return
        }
        viewModelScope.launch {
            val akun = Akun(
                organizationId = org.id,
                nama = nama.trim(),
                saldoAwal = saldoAwal
            )
            repository.insertAkun(akun)
            postToast("Akun \"${akun.nama}\" ditambahkan")
            _accountsWithSaldo.value = repository.getAkunWithSaldoList(org.id)
        }
    }

    fun deleteAccount(akunId: String) {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            repository.deleteAkun(akunId, org.id)
            postToast("Akun berhasil dihapus")
            _accountsWithSaldo.value = repository.getAkunWithSaldoList(org.id)
        }
    }

    fun addCategory(nama: String, tipe: String) {
        val org = _currentOrg.value ?: return
        if (nama.isBlank()) {
            postToast("Nama kategori wajib diisi")
            return
        }
        viewModelScope.launch {
            val kat = Kategori(
                organizationId = org.id,
                nama = nama.trim(),
                tipe = tipe
            )
            repository.insertKategori(kat)
            postToast("Kategori \"${kat.nama}\" ditambahkan")
        }
    }

    fun deleteCategory(kategoriId: String) {
        val org = _currentOrg.value ?: return
        viewModelScope.launch {
            repository.deleteKategori(kategoriId, org.id)
            postToast("Kategori berhasil dihapus")
        }
    }
}
