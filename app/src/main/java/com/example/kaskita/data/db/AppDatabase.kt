package com.example.kaskita.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kaskita.data.dao.KasKitaDao
import com.example.kaskita.data.model.*
import com.example.kaskita.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        User::class,
        Organization::class,
        OrganizationMember::class,
        Akun::class,
        Anggota::class,
        IuranSettings::class,
        Kategori::class,
        Transaksi::class,
        IuranPembayaran::class,
        IuranAlokasi::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun kasKitaDao(): KasKitaDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kaskita_database"
                )
                .addCallback(DatabaseCallback(scope))
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database.kasKitaDao())
                    }
                }
            }
        }

        val DEFAULT_CATEGORIES = listOf(
            Pair("Donasi", "masuk"),
            Pair("Pendapatan Kegiatan", "masuk"),
            Pair("Lain-lain (Masuk)", "masuk"),
            Pair("Kegiatan", "keluar"),
            Pair("Konsumsi", "keluar"),
            Pair("Perlengkapan", "keluar"),
            Pair("Transportasi", "keluar"),
            Pair("Administrasi", "keluar"),
            Pair("Sumbangan/Bantuan", "keluar"),
            Pair("Perawatan", "keluar"),
            Pair("Lainnya", "keluar")
        )

        private suspend fun populateInitialData(dao: KasKitaDao) {
            val userId = "user-default-1"
            val user = User(
                id = userId,
                email = "bendahara@kaskita.id",
                nama = "Bendahara Organisasi",
                passwordHash = "demo",
                passwordSalt = "demo"
            )
            dao.insertUser(user)

            val orgId = "org-default-1"
            val org = Organization(
                id = orgId,
                nama = "Kas RT 05 / RW 02",
                inviteCode = "KASKITA8",
                createdBy = userId
            )
            dao.insertOrganization(org)

            val member = OrganizationMember(
                id = UUID.randomUUID().toString(),
                userId = userId,
                organizationId = orgId
            )
            dao.insertOrganizationMember(member)

            val today = DateUtils.todayStr()
            val startMonth = DateUtils.periodAdd(DateUtils.currentPeriode(), -2) + "-01"

            val settings = IuranSettings(
                organizationId = orgId,
                namaIuran = "Iuran Warga Bulanan",
                nominal = 25000.0,
                tanggalMulai = startMonth
            )
            dao.insertOrUpdateIuranSettings(settings)

            val akunUtama = Akun(
                id = "akun-kas-utama",
                organizationId = orgId,
                nama = "Kas Tunai",
                saldoAwal = 500000.0
            )
            val akunBank = Akun(
                id = "akun-bank-bca",
                organizationId = orgId,
                nama = "Rekening Bank BCA",
                saldoAwal = 1200000.0
            )
            dao.insertAkun(akunUtama)
            dao.insertAkun(akunBank)

            val kategoriList = DEFAULT_CATEGORIES.map { (nama, tipe) ->
                Kategori(
                    id = UUID.randomUUID().toString(),
                    organizationId = orgId,
                    nama = nama,
                    tipe = tipe
                )
            }
            dao.insertKategoriBatch(kategoriList)

            // Initial Anggota
            val anggota1 = Anggota(
                id = "anggota-1",
                organizationId = orgId,
                nama = "Budi Santoso",
                noHp = "081234567890",
                catatan = "Blok A1 No. 4",
                tanggalGabung = startMonth
            )
            val anggota2 = Anggota(
                id = "anggota-2",
                organizationId = orgId,
                nama = "Siti Rahmawati",
                noHp = "081987654321",
                catatan = "Blok A2 No. 12",
                tanggalGabung = startMonth
            )
            val anggota3 = Anggota(
                id = "anggota-3",
                organizationId = orgId,
                nama = "Ahmad Hidayat",
                noHp = "085611223344",
                catatan = "Blok B1 No. 2",
                tanggalGabung = startMonth
            )
            val anggota4 = Anggota(
                id = "anggota-4",
                organizationId = orgId,
                nama = "Dewi Lestari",
                noHp = "082155667788",
                catatan = "Blok B3 No. 8",
                tanggalGabung = DateUtils.todayStr()
            )
            dao.insertAnggota(anggota1)
            dao.insertAnggota(anggota2)
            dao.insertAnggota(anggota3)
            dao.insertAnggota(anggota4)

            // Initial transactions
            val t1 = Transaksi(
                id = UUID.randomUUID().toString(),
                organizationId = orgId,
                tipe = "masuk",
                sumber = "umum",
                kategori = "Donasi",
                jumlah = 350000.0,
                catatan = "Donasi Pembangunan Gapura",
                metode = "Transfer Bank",
                akunId = akunBank.id,
                tanggal = today,
                createdBy = userId
            )
            val t2 = Transaksi(
                id = UUID.randomUUID().toString(),
                organizationId = orgId,
                tipe = "keluar",
                sumber = "umum",
                kategori = "Perlengkapan",
                jumlah = 120000.0,
                catatan = "Pembelian Lampu Jalan Gang",
                metode = "Tunai",
                akunId = akunUtama.id,
                tanggal = today,
                createdBy = userId
            )
            val t3 = Transaksi(
                id = UUID.randomUUID().toString(),
                organizationId = orgId,
                tipe = "keluar",
                sumber = "umum",
                kategori = "Konsumsi",
                jumlah = 85000.0,
                catatan = "Snack Kerja Bakti Mingguan",
                metode = "Tunai",
                akunId = akunUtama.id,
                tanggal = today,
                createdBy = userId
            )
            dao.insertTransaksi(t1)
            dao.insertTransaksi(t2)
            dao.insertTransaksi(t3)
        }
    }
}
