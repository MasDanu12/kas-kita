package com.example.kaskita.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.kaskita.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface KasKitaDao {
    // ---- Users ----
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: String): User?

    @Query("SELECT * FROM users LIMIT 1")
    suspend fun getFirstUser(): User?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUser(id: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    // ---- Organizations ----
    @Query("""
        SELECT o.* FROM organizations o
        INNER JOIN organization_members m ON m.organizationId = o.id
        WHERE m.userId = :userId
        ORDER BY o.createdAt DESC
    """)
    fun getOrganizationsForUser(userId: String): Flow<List<Organization>>

    @Query("""
        SELECT o.* FROM organizations o
        INNER JOIN organization_members m ON m.organizationId = o.id
        WHERE m.userId = :userId
        ORDER BY o.createdAt DESC
    """)
    suspend fun getOrganizationsForUserOnce(userId: String): List<Organization>

    @Query("SELECT * FROM organizations WHERE id = :id LIMIT 1")
    suspend fun getOrganizationById(id: String): Organization?

    @Query("SELECT * FROM organizations WHERE id = :id")
    fun observeOrganization(id: String): Flow<Organization?>

    @Query("SELECT * FROM organizations WHERE UPPER(inviteCode) = UPPER(:inviteCode) LIMIT 1")
    suspend fun getOrganizationByInviteCode(inviteCode: String): Organization?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganization(org: Organization)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganizationMember(member: OrganizationMember)

    @Query("SELECT * FROM organization_members WHERE userId = :userId AND organizationId = :orgId LIMIT 1")
    suspend fun getMemberRecord(userId: String, orgId: String): OrganizationMember?

    // ---- Akun ----
    @Query("SELECT * FROM akun WHERE organizationId = :orgId AND aktif = 1 ORDER BY createdAt ASC")
    fun getAkunList(orgId: String): Flow<List<Akun>>

    @Query("SELECT * FROM akun WHERE organizationId = :orgId ORDER BY createdAt ASC")
    suspend fun getAllAkunOnce(orgId: String): List<Akun>

    @Query("SELECT * FROM akun WHERE id = :id LIMIT 1")
    suspend fun getAkunById(id: String): Akun?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAkun(akun: Akun)

    @Update
    suspend fun updateAkun(akun: Akun)

    @Query("DELETE FROM akun WHERE id = :id AND organizationId = :orgId")
    suspend fun deleteAkun(id: String, orgId: String)

    // ---- Anggota ----
    @Query("SELECT * FROM anggota WHERE organizationId = :orgId ORDER BY nama ASC")
    fun getAnggotaList(orgId: String): Flow<List<Anggota>>

    @Query("SELECT * FROM anggota WHERE organizationId = :orgId AND aktif = 1 ORDER BY nama ASC")
    suspend fun getActiveAnggotaOnce(orgId: String): List<Anggota>

    @Query("SELECT * FROM anggota WHERE organizationId = :orgId ORDER BY nama ASC")
    suspend fun getAllAnggotaOnce(orgId: String): List<Anggota>

    @Query("SELECT * FROM anggota WHERE id = :id LIMIT 1")
    suspend fun getAnggotaById(id: String): Anggota?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnggota(anggota: Anggota)

    @Update
    suspend fun updateAnggota(anggota: Anggota)

    @Query("DELETE FROM anggota WHERE id = :id AND organizationId = :orgId")
    suspend fun deleteAnggota(id: String, orgId: String)

    // ---- Iuran Settings ----
    @Query("SELECT * FROM iuran_settings WHERE organizationId = :orgId LIMIT 1")
    fun getIuranSettings(orgId: String): Flow<IuranSettings?>

    @Query("SELECT * FROM iuran_settings WHERE organizationId = :orgId LIMIT 1")
    suspend fun getIuranSettingsOnce(orgId: String): IuranSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateIuranSettings(settings: IuranSettings)

    // ---- Kategori ----
    @Query("SELECT * FROM kategori WHERE organizationId = :orgId ORDER BY tipe, nama ASC")
    fun getKategoriList(orgId: String): Flow<List<Kategori>>

    @Query("SELECT * FROM kategori WHERE organizationId = :orgId ORDER BY tipe, nama ASC")
    suspend fun getKategoriListOnce(orgId: String): List<Kategori>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKategori(kategori: Kategori)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKategoriBatch(kategoriList: List<Kategori>)

    @Query("DELETE FROM kategori WHERE id = :id AND organizationId = :orgId")
    suspend fun deleteKategori(id: String, orgId: String)

    // ---- Transaksi ----
    @Query("""
        SELECT t.*, a.nama AS anggotaNama, ak.nama AS akunNama, ak2.nama AS akunTujuanNama
        FROM transaksi t
        LEFT JOIN anggota a ON a.id = t.anggotaId
        LEFT JOIN akun ak ON ak.id = t.akunId
        LEFT JOIN akun ak2 ON ak2.id = t.akunTujuanId
        WHERE t.organizationId = :orgId
        ORDER BY t.tanggal DESC, t.createdAt DESC
        LIMIT 500
    """)
    fun getTransaksiWithDetails(orgId: String): Flow<List<TransaksiWithDetails>>

    @Query("""
        SELECT t.*, a.nama AS anggotaNama, ak.nama AS akunNama, ak2.nama AS akunTujuanNama
        FROM transaksi t
        LEFT JOIN anggota a ON a.id = t.anggotaId
        LEFT JOIN akun ak ON ak.id = t.akunId
        LEFT JOIN akun ak2 ON ak2.id = t.akunTujuanId
        WHERE t.organizationId = :orgId
        ORDER BY t.tanggal DESC, t.createdAt DESC
        LIMIT :limit
    """)
    fun getRecentTransaksiWithDetails(orgId: String, limit: Int = 5): Flow<List<TransaksiWithDetails>>

    @Query("""
        SELECT t.*, a.nama AS anggotaNama, ak.nama AS akunNama, ak2.nama AS akunTujuanNama
        FROM transaksi t
        LEFT JOIN anggota a ON a.id = t.anggotaId
        LEFT JOIN akun ak ON ak.id = t.akunId
        LEFT JOIN akun ak2 ON ak2.id = t.akunTujuanId
        WHERE t.id = :id AND t.organizationId = :orgId
        LIMIT 1
    """)
    suspend fun getTransaksiDetailsById(id: String, orgId: String): TransaksiWithDetails?

    @Query("SELECT * FROM transaksi WHERE id = :id AND organizationId = :orgId LIMIT 1")
    suspend fun getTransaksiById(id: String, orgId: String): Transaksi?

    @Query("SELECT * FROM transaksi WHERE organizationId = :orgId")
    suspend fun getAllTransaksiOnce(orgId: String): List<Transaksi>

    @Query("SELECT * FROM transaksi WHERE organizationId = :orgId AND SUBSTR(tanggal, 1, 7) = :periode")
    suspend fun getTransaksiForMonth(orgId: String, periode: String): List<Transaksi>

    @Query("SELECT * FROM transaksi WHERE organizationId = :orgId AND SUBSTR(tanggal, 1, 4) = :year")
    suspend fun getTransaksiForYear(orgId: String, year: String): List<Transaksi>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaksi(transaksi: Transaksi)

    @Query("DELETE FROM transaksi WHERE id = :id AND organizationId = :orgId")
    suspend fun deleteTransaksi(id: String, orgId: String)

    // ---- Iuran Pembayaran & Alokasi ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIuranPembayaran(pembayaran: IuranPembayaran)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIuranAlokasiBatch(alokasiList: List<IuranAlokasi>)

    @Query("SELECT * FROM iuran_pembayaran WHERE organizationId = :orgId AND anggotaId = :anggotaId ORDER BY tanggalBayar DESC, createdAt DESC")
    fun getPembayaranForMember(orgId: String, anggotaId: String): Flow<List<IuranPembayaran>>

    @Query("SELECT * FROM iuran_pembayaran WHERE transaksiId = :transaksiId LIMIT 1")
    suspend fun getPembayaranByTransaksiId(transaksiId: String): IuranPembayaran?

    @Query("SELECT * FROM iuran_alokasi WHERE pembayaranId = :pembayaranId ORDER BY periode ASC")
    suspend fun getAlokasiForPembayaran(pembayaranId: String): List<IuranAlokasi>

    @Query("SELECT * FROM iuran_alokasi WHERE organizationId = :orgId AND anggotaId = :anggotaId")
    suspend fun getAllAlokasiForMember(orgId: String, anggotaId: String): List<IuranAlokasi>

    @Query("SELECT * FROM iuran_alokasi WHERE organizationId = :orgId")
    suspend fun getAllAlokasiForOrg(orgId: String): List<IuranAlokasi>

    @Query("DELETE FROM iuran_alokasi WHERE pembayaranId = :pembayaranId")
    suspend fun deleteAlokasiForPembayaran(pembayaranId: String)

    @Query("DELETE FROM iuran_pembayaran WHERE id = :id")
    suspend fun deleteIuranPembayaran(id: String)
}
