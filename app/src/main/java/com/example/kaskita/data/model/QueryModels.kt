package com.example.kaskita.data.model

data class TransaksiWithDetails(
    val id: String,
    val organizationId: String,
    val tipe: String, // masuk, keluar, transfer, penyesuaian
    val sumber: String, // umum, iuran
    val kategori: String?,
    val jumlah: Double,
    val catatan: String?,
    val metode: String?,
    val anggotaId: String?,
    val akunId: String?,
    val akunTujuanId: String?,
    val tanggal: String,
    val createdBy: String,
    val createdAt: Long,
    val anggotaNama: String?,
    val akunNama: String?,
    val akunTujuanNama: String?
)

data class AkunWithSaldo(
    val id: String,
    val organizationId: String,
    val nama: String,
    val saldoAwal: Double,
    val aktif: Boolean,
    val saldo: Double
)

data class MemberDuesStatus(
    val anggotaId: String,
    val nama: String,
    val noHp: String?,
    val status: String, // "lunas", "sebagian", "belum_bayar", "tidak_dikenakan"
    val dibayar: Double,
    val wajib: Double,
    val lunasSampai: String? // "YYYY-MM"
)

data class DuesOverview(
    val periode: String,
    val totalAnggota: Int,
    val lunas: Int,
    val sebagian: Int,
    val menunggak: Int,
    val terkumpul: Double,
    val tunggakan: Double,
    val statusList: List<MemberDuesStatus>
)

data class MemberArrears(
    val anggotaId: String,
    val nama: String,
    val noHp: String?,
    val totalTunggakan: Double
)

data class CategoryExpense(
    val nama: String,
    val total: Double,
    val percentage: Float
)

data class MonthlyReportData(
    val bulan: String, // "YYYY-MM"
    val totalMasuk: Double,
    val totalKeluar: Double,
    val saldoBersih: Double,
    val jumlahTransaksi: Int,
    val kategoriExpenses: List<CategoryExpense>,
    val iuranOverview: DuesOverview,
    val transaksiList: List<TransaksiWithDetails>
)

data class MonthTrend(
    val bulan: String, // "YYYY-MM"
    val bulanLabel: String, // "Jan", "Feb", ...
    val masuk: Double,
    val keluar: Double,
    val net: Double
)

data class AnnualReportData(
    val tahun: String,
    val totalMasuk: Double,
    val totalKeluar: Double,
    val saldoBersih: Double,
    val jumlahTransaksi: Int,
    val monthsTrend: List<MonthTrend>
)

data class ReceiptData(
    val id: String,
    val organisasiNama: String,
    val tipe: String,
    val kategori: String?,
    val jumlah: Double,
    val tanggal: String,
    val catatan: String?,
    val anggotaNama: String?,
    val akunNama: String?,
    val periodeList: List<String> = emptyList()
)
