package com.example.kaskita.data.repository

import com.example.kaskita.data.dao.KasKitaDao
import com.example.kaskita.data.db.AppDatabase
import com.example.kaskita.data.model.*
import com.example.kaskita.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.security.SecureRandom
import java.util.UUID

class KasKitaRepository(private val dao: KasKitaDao) {

    // ---- User & Auth ----
    fun observeUser(userId: String): Flow<User?> = dao.observeUser(userId)
    suspend fun getUserById(userId: String): User? = dao.getUserById(userId)
    suspend fun getUserByEmail(email: String): User? = dao.getUserByEmail(email)
    suspend fun getFirstUser(): User? = dao.getFirstUser()

    suspend fun registerUser(email: String, nama: String, passwordHash: String): User {
        val user = User(
            id = UUID.randomUUID().toString(),
            email = email.trim().lowercase(),
            nama = nama.trim(),
            passwordHash = passwordHash
        )
        dao.insertUser(user)
        return user
    }

    suspend fun updateUser(user: User) = dao.updateUser(user)

    // ---- Organization ----
    fun getOrganizationsForUser(userId: String): Flow<List<Organization>> =
        dao.getOrganizationsForUser(userId)

    suspend fun getOrganizationsForUserOnce(userId: String): List<Organization> =
        dao.getOrganizationsForUserOnce(userId)

    suspend fun getOrganizationById(id: String): Organization? =
        dao.getOrganizationById(id)

    fun observeOrganization(id: String): Flow<Organization?> =
        dao.observeOrganization(id)

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val random = SecureRandom()
        return (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    suspend fun createOrganization(userId: String, namaOrg: String): Organization {
        val orgId = UUID.randomUUID().toString()
        var code = generateInviteCode()
        for (i in 0..4) {
            val existing = dao.getOrganizationByInviteCode(code)
            if (existing == null) break
            code = generateInviteCode()
        }

        val org = Organization(
            id = orgId,
            nama = namaOrg.trim(),
            inviteCode = code,
            createdBy = userId
        )
        dao.insertOrganization(org)

        val member = OrganizationMember(
            id = UUID.randomUUID().toString(),
            userId = userId,
            organizationId = orgId
        )
        dao.insertOrganizationMember(member)

        val settings = IuranSettings(
            organizationId = orgId,
            namaIuran = "Iuran Bulanan",
            nominal = 0.0,
            tanggalMulai = DateUtils.todayStr()
        )
        dao.insertOrUpdateIuranSettings(settings)

        val defaultAkun = Akun(
            id = UUID.randomUUID().toString(),
            organizationId = orgId,
            nama = "Kas Utama",
            saldoAwal = 0.0
        )
        dao.insertAkun(defaultAkun)

        val kategoriList = AppDatabase.DEFAULT_CATEGORIES.map { (nama, tipe) ->
            Kategori(
                id = UUID.randomUUID().toString(),
                organizationId = orgId,
                nama = nama,
                tipe = tipe
            )
        }
        dao.insertKategoriBatch(kategoriList)

        return org
    }

    suspend fun joinOrganization(userId: String, inviteCode: String): Result<Organization> {
        val org = dao.getOrganizationByInviteCode(inviteCode.trim().uppercase())
            ?: return Result.failure(Exception("Kode undangan tidak ditemukan"))

        val already = dao.getMemberRecord(userId, org.id)
        if (already != null) {
            return Result.failure(Exception("Anda sudah tergabung dalam organisasi ini"))
        }

        val member = OrganizationMember(
            id = UUID.randomUUID().toString(),
            userId = userId,
            organizationId = org.id
        )
        dao.insertOrganizationMember(member)
        return Result.success(org)
    }

    // ---- Akun & Balance Calculation ----
    fun getAkunList(orgId: String): Flow<List<Akun>> = dao.getAkunList(orgId)

    suspend fun getAkunWithSaldoList(orgId: String): List<AkunWithSaldo> {
        val accounts = dao.getAllAkunOnce(orgId)
        val allTrx = dao.getAllTransaksiOnce(orgId)

        return accounts.map { akun ->
            var saldo = akun.saldoAwal
            for (t in allTrx) {
                when (t.tipe) {
                    "masuk" -> if (t.akunId == akun.id) saldo += t.jumlah
                    "keluar" -> if (t.akunId == akun.id) saldo -= t.jumlah
                    "transfer" -> {
                        if (t.akunId == akun.id) saldo -= t.jumlah
                        if (t.akunTujuanId == akun.id) saldo += t.jumlah
                    }
                    "penyesuaian" -> if (t.akunId == akun.id) saldo += t.jumlah
                }
            }
            AkunWithSaldo(
                id = akun.id,
                organizationId = akun.organizationId,
                nama = akun.nama,
                saldoAwal = akun.saldoAwal,
                aktif = akun.aktif,
                saldo = saldo
            )
        }
    }

    suspend fun insertAkun(akun: Akun) = dao.insertAkun(akun)
    suspend fun updateAkun(akun: Akun) = dao.updateAkun(akun)
    suspend fun deleteAkun(id: String, orgId: String) = dao.deleteAkun(id, orgId)

    // ---- Anggota ----
    fun getAnggotaList(orgId: String): Flow<List<Anggota>> = dao.getAnggotaList(orgId)
    suspend fun insertAnggota(anggota: Anggota) = dao.insertAnggota(anggota)
    suspend fun updateAnggota(anggota: Anggota) = dao.updateAnggota(anggota)
    suspend fun deleteAnggota(id: String, orgId: String) = dao.deleteAnggota(id, orgId)

    // ---- Iuran Settings ----
    fun getIuranSettings(orgId: String): Flow<IuranSettings?> = dao.getIuranSettings(orgId)
    suspend fun updateIuranSettings(settings: IuranSettings) = dao.insertOrUpdateIuranSettings(settings)

    // ---- Kategori ----
    fun getKategoriList(orgId: String): Flow<List<Kategori>> = dao.getKategoriList(orgId)
    suspend fun insertKategori(kategori: Kategori) = dao.insertKategori(kategori)
    suspend fun deleteKategori(id: String, orgId: String) = dao.deleteKategori(id, orgId)

    // ---- Transaksi ----
    fun getTransaksiWithDetails(orgId: String): Flow<List<TransaksiWithDetails>> =
        dao.getTransaksiWithDetails(orgId)

    fun getRecentTransaksiWithDetails(orgId: String, limit: Int = 5): Flow<List<TransaksiWithDetails>> =
        dao.getRecentTransaksiWithDetails(orgId, limit)

    suspend fun insertTransaksi(transaksi: Transaksi) = dao.insertTransaksi(transaksi)

    suspend fun deleteTransaksi(id: String, orgId: String): Result<Unit> {
        val t = dao.getTransaksiById(id, orgId) ?: return Result.failure(Exception("Transaksi tidak ditemukan"))
        if (t.sumber == "iuran") {
            val pemb = dao.getPembayaranByTransaksiId(id)
            if (pemb != null) {
                dao.deleteAlokasiForPembayaran(pemb.id)
                dao.deleteIuranPembayaran(pemb.id)
            }
        }
        dao.deleteTransaksi(id, orgId)
        return Result.success(Unit)
    }

    suspend fun getReceiptData(transaksiId: String, orgId: String): ReceiptData? {
        val t = dao.getTransaksiDetailsById(transaksiId, orgId) ?: return null
        val org = dao.getOrganizationById(orgId)
        var periodeList = emptyList<String>()

        if (t.sumber == "iuran") {
            val pemb = dao.getPembayaranByTransaksiId(transaksiId)
            if (pemb != null) {
                periodeList = dao.getAlokasiForPembayaran(pemb.id).map { it.periode }
            }
        }

        return ReceiptData(
            id = t.id,
            organisasiNama = org?.nama ?: "Kas Kita",
            tipe = t.tipe,
            kategori = t.kategori,
            jumlah = t.jumlah,
            tanggal = t.tanggal,
            catatan = t.catatan,
            anggotaNama = t.anggotaNama,
            akunNama = t.akunNama,
            periodeList = periodeList
        )
    }

    // ---- Dues Logic (FIFO Allocation & Status Computation) ----
    suspend fun getDuesOverview(orgId: String, targetPeriode: String = DateUtils.currentPeriode()): DuesOverview {
        val settings = dao.getIuranSettingsOnce(orgId) ?: IuranSettings(organizationId = orgId, nominal = 0.0, tanggalMulai = DateUtils.todayStr())
        val members = dao.getActiveAnggotaOnce(orgId)
        val allAlokasi = dao.getAllAlokasiForOrg(orgId)

        val alokasiByMember = allAlokasi.groupBy { it.anggotaId }

        val statusList = members.map { member ->
            val orgStart = DateUtils.periodeFromDateStr(settings.tanggalMulai)
            val memberStart = DateUtils.periodeFromDateStr(member.tanggalGabung)
            val startPeriode = DateUtils.periodeMax(orgStart, memberStart)

            val memberAlokasiList = alokasiByMember[member.id] ?: emptyList()
            val alokasiMap = memberAlokasiList.groupBy { it.periode }
                .mapValues { entry -> entry.value.sumOf { it.jumlah } }

            val status: String
            val dibayar: Double
            val wajib: Double

            if (targetPeriode < startPeriode) {
                status = "tidak_dikenakan"
                dibayar = 0.0
                wajib = 0.0
            } else {
                wajib = settings.nominal
                dibayar = alokasiMap[targetPeriode] ?: 0.0
                status = when {
                    dibayar >= settings.nominal - 0.01 -> "lunas"
                    dibayar > 0.0 -> "sebagian"
                    else -> "belum_bayar"
                }
            }

            // Compute lunas sampai
            var p = startPeriode
            var lunasSampai: String? = null
            var guard = 0
            val maxPeriodCheck = if (targetPeriode > DateUtils.currentPeriode()) targetPeriode else DateUtils.currentPeriode()
            while (p <= maxPeriodCheck && guard < 600) {
                guard++
                val paid = alokasiMap[p] ?: 0.0
                if (paid >= settings.nominal - 0.01 && settings.nominal > 0) {
                    lunasSampai = p
                    p = DateUtils.periodAdd(p, 1)
                } else {
                    break
                }
            }

            MemberDuesStatus(
                anggotaId = member.id,
                nama = member.nama,
                noHp = member.noHp,
                status = status,
                dibayar = dibayar,
                wajib = wajib,
                lunasSampai = lunasSampai
            )
        }

        val lunasCount = statusList.count { it.status == "lunas" }
        val sebagianCount = statusList.count { it.status == "sebagian" }
        val belumCount = statusList.count { it.status == "belum_bayar" }
        val terkumpul = statusList.filter { it.status != "tidak_dikenakan" }.sumOf { it.dibayar }
        val tunggakan = statusList.filter { it.status != "tidak_dikenakan" }.sumOf { maxOf(0.0, it.wajib - it.dibayar) }

        return DuesOverview(
            periode = targetPeriode,
            totalAnggota = statusList.size,
            lunas = lunasCount,
            sebagian = sebagianCount,
            menunggak = belumCount,
            terkumpul = terkumpul,
            tunggakan = tunggakan,
            statusList = statusList
        )
    }

    suspend fun getMemberArrearsList(orgId: String, targetPeriode: String = DateUtils.currentPeriode()): List<MemberArrears> {
        val settings = dao.getIuranSettingsOnce(orgId) ?: return emptyList()
        if (settings.nominal <= 0) return emptyList()
        val members = dao.getActiveAnggotaOnce(orgId)
        val allAlokasi = dao.getAllAlokasiForOrg(orgId)
        val alokasiByMember = allAlokasi.groupBy { it.anggotaId }

        val arrearsList = mutableListOf<MemberArrears>()

        for (member in members) {
            val orgStart = DateUtils.periodeFromDateStr(settings.tanggalMulai)
            val memberStart = DateUtils.periodeFromDateStr(member.tanggalGabung)
            val startPeriode = DateUtils.periodeMax(orgStart, memberStart)

            if (targetPeriode < startPeriode) continue

            val memberAlokasiList = alokasiByMember[member.id] ?: emptyList()
            val alokasiMap = memberAlokasiList.groupBy { it.periode }
                .mapValues { entry -> entry.value.sumOf { it.jumlah } }

            var totalTunggakan = 0.0
            var p = startPeriode
            var guard = 0
            while (p <= targetPeriode && guard < 600) {
                guard++
                val dibayar = alokasiMap[p] ?: 0.0
                totalTunggakan += maxOf(0.0, settings.nominal - dibayar)
                p = DateUtils.periodAdd(p, 1)
            }

            if (totalTunggakan > 0.01) {
                arrearsList.add(
                    MemberArrears(
                        anggotaId = member.id,
                        nama = member.nama,
                        noHp = member.noHp,
                        totalTunggakan = totalTunggakan
                    )
                )
            }
        }
        return arrearsList
    }

    suspend fun payDues(
        orgId: String,
        userId: String,
        anggotaId: String,
        jumlah: Double,
        tanggal: String,
        akunId: String?,
        catatan: String?
    ): Result<ReceiptData> {
        val member = dao.getAnggotaById(anggotaId) ?: return Result.failure(Exception("Anggota tidak ditemukan"))
        val settings = dao.getIuranSettingsOnce(orgId) ?: return Result.failure(Exception("Pengaturan iuran belum diatur"))
        if (settings.nominal <= 0) return Result.failure(Exception("Nominal iuran belum diatur atau 0"))

        val orgStart = DateUtils.periodeFromDateStr(settings.tanggalMulai)
        val memberStart = DateUtils.periodeFromDateStr(member.tanggalGabung)
        val startPeriode = DateUtils.periodeMax(orgStart, memberStart)

        val existingAlokasi = dao.getAllAlokasiForMember(orgId, anggotaId)
        val alokasiMap = existingAlokasi.groupBy { it.periode }
            .mapValues { entry -> entry.value.sumOf { it.jumlah } }
            .toMutableMap()

        // FIFO allocation
        var remaining = jumlah
        var currentP = startPeriode
        val newAllocations = mutableListOf<Pair<String, Double>>()
        var guard = 0

        while (remaining > 0.009 && guard < 600) {
            guard++
            val alreadyPaid = alokasiMap[currentP] ?: 0.0
            val needed = settings.nominal - alreadyPaid
            if (needed <= 0.009) {
                currentP = DateUtils.periodAdd(currentP, 1)
                continue
            }
            val allocAmount = minOf(needed, remaining)
            newAllocations.add(Pair(currentP, allocAmount))
            alokasiMap[currentP] = alreadyPaid + allocAmount
            remaining -= allocAmount
            if (allocAmount < needed - 0.009) {
                break
            }
            currentP = DateUtils.periodAdd(currentP, 1)
        }

        if (newAllocations.isEmpty()) {
            return Result.failure(Exception("Semua periode sudah lunas"))
        }

        val effectiveAkunId = akunId ?: dao.getAllAkunOnce(orgId).firstOrNull()?.id
        val transaksiId = UUID.randomUUID().toString()
        val org = dao.getOrganizationById(orgId)

        val trx = Transaksi(
            id = transaksiId,
            organizationId = orgId,
            tipe = "masuk",
            sumber = "iuran",
            kategori = settings.namaIuran,
            jumlah = jumlah,
            catatan = catatan ?: "Iuran (${member.nama})",
            metode = "Tunai",
            anggotaId = anggotaId,
            akunId = effectiveAkunId,
            tanggal = tanggal,
            createdBy = userId
        )
        dao.insertTransaksi(trx)

        val pembayaranId = UUID.randomUUID().toString()
        val pemb = IuranPembayaran(
            id = pembayaranId,
            organizationId = orgId,
            anggotaId = anggotaId,
            jumlahTotal = jumlah,
            tanggalBayar = tanggal,
            transaksiId = transaksiId,
            catatan = catatan
        )
        dao.insertIuranPembayaran(pemb)

        val alokasiEntities = newAllocations.map { (periode, allocJumlah) ->
            IuranAlokasi(
                id = UUID.randomUUID().toString(),
                organizationId = orgId,
                anggotaId = anggotaId,
                pembayaranId = pembayaranId,
                periode = periode,
                jumlah = allocJumlah
            )
        }
        dao.insertIuranAlokasiBatch(alokasiEntities)

        val receipt = ReceiptData(
            id = transaksiId,
            organisasiNama = org?.nama ?: "Kas Kita",
            tipe = "masuk",
            kategori = settings.namaIuran,
            jumlah = jumlah,
            tanggal = tanggal,
            catatan = catatan,
            anggotaNama = member.nama,
            akunNama = effectiveAkunId?.let { dao.getAkunById(it)?.nama },
            periodeList = newAllocations.map { it.first }
        )

        return Result.success(receipt)
    }

    suspend fun getMemberPaymentHistory(orgId: String, anggotaId: String): List<Pair<IuranPembayaran, List<IuranAlokasi>>> {
        val payments = dao.getAllAlokasiForMember(orgId, anggotaId)
        val paymentGroups = payments.groupBy { it.pembayaranId }

        val result = mutableListOf<Pair<IuranPembayaran, List<IuranAlokasi>>>()
        for ((pembId, alokasiList) in paymentGroups) {
            val pemb = dao.getPembayaranByTransaksiId(alokasiList.first().pembayaranId)
            // Or get from DB if needed
        }
        return result
    }

    // ---- Reports ----
    suspend fun getMonthlyReport(orgId: String, bulan: String = DateUtils.currentPeriode()): MonthlyReportData {
        val trxList = dao.getTransaksiForMonth(orgId, bulan)
        val totalMasuk = trxList.filter { it.tipe == "masuk" }.sumOf { it.jumlah }
        val totalKeluar = trxList.filter { it.tipe == "keluar" }.sumOf { it.jumlah }
        val saldoBersih = totalMasuk - totalKeluar

        val expenseTrx = trxList.filter { it.tipe == "keluar" }
        val categoryExpenses = expenseTrx.groupBy { it.kategori ?: "Lainnya" }
            .map { (kategori, list) ->
                val sum = list.sumOf { it.jumlah }
                val pct = if (totalKeluar > 0) (sum / totalKeluar).toFloat() else 0f
                CategoryExpense(nama = kategori, total = sum, percentage = pct)
            }
            .sortedByDescending { it.total }

        val duesOverview = getDuesOverview(orgId, bulan)
        val allWithDetails = dao.getAllTransaksiOnce(orgId)
        val accounts = dao.getAllAkunOnce(orgId).associateBy { it.id }
        val members = dao.getAllAnggotaOnce(orgId).associateBy { it.id }

        val monthlyDetails = trxList.map { t ->
            TransaksiWithDetails(
                id = t.id,
                organizationId = t.organizationId,
                tipe = t.tipe,
                sumber = t.sumber,
                kategori = t.kategori,
                jumlah = t.jumlah,
                catatan = t.catatan,
                metode = t.metode,
                anggotaId = t.anggotaId,
                akunId = t.akunId,
                akunTujuanId = t.akunTujuanId,
                tanggal = t.tanggal,
                createdBy = t.createdBy,
                createdAt = t.createdAt,
                anggotaNama = t.anggotaId?.let { members[it]?.nama },
                akunNama = t.akunId?.let { accounts[it]?.nama },
                akunTujuanNama = t.akunTujuanId?.let { accounts[it]?.nama }
            )
        }

        return MonthlyReportData(
            bulan = bulan,
            totalMasuk = totalMasuk,
            totalKeluar = totalKeluar,
            saldoBersih = saldoBersih,
            jumlahTransaksi = trxList.size,
            kategoriExpenses = categoryExpenses,
            iuranOverview = duesOverview,
            transaksiList = monthlyDetails
        )
    }

    suspend fun getAnnualReport(orgId: String, tahun: String = DateUtils.currentYear()): AnnualReportData {
        val trxList = dao.getTransaksiForYear(orgId, tahun)
        val totalMasuk = trxList.filter { it.tipe == "masuk" }.sumOf { it.jumlah }
        val totalKeluar = trxList.filter { it.tipe == "keluar" }.sumOf { it.jumlah }
        val saldoBersih = totalMasuk - totalKeluar

        val monthLabels = listOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Ags", "Sep", "Okt", "Nov", "Des")
        val trends = (1..12).map { month ->
            val monthStr = String.format(java.util.Locale.US, "%s-%02d", tahun, month)
            val monthTrx = trxList.filter { it.tanggal.startsWith(monthStr) }
            val mMasuk = monthTrx.filter { it.tipe == "masuk" }.sumOf { it.jumlah }
            val mKeluar = monthTrx.filter { it.tipe == "keluar" }.sumOf { it.jumlah }
            MonthTrend(
                bulan = monthStr,
                bulanLabel = monthLabels[month - 1],
                masuk = mMasuk,
                keluar = mKeluar,
                net = mMasuk - mKeluar
            )
        }

        return AnnualReportData(
            tahun = tahun,
            totalMasuk = totalMasuk,
            totalKeluar = totalKeluar,
            saldoBersih = saldoBersih,
            jumlahTransaksi = trxList.size,
            monthsTrend = trends
        )
    }
}
