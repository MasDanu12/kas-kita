package com.example.kaskita.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class User(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val email: String,
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val nama: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "organizations",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["createdBy"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("createdBy"), Index(value = ["inviteCode"], unique = true)]
)
data class Organization(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val nama: String,
    val inviteCode: String,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "organization_members",
    foreignKeys = [
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("userId"),
        Index("organizationId"),
        Index(value = ["userId", "organizationId"], unique = true)
    ]
)
data class OrganizationMember(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val organizationId: String,
    val joinedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "akun",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("organizationId")]
)
data class Akun(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val nama: String,
    val saldoAwal: Double = 0.0,
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "anggota",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("organizationId")]
)
data class Anggota(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val nama: String,
    val noHp: String? = null,
    val catatan: String? = null,
    val tanggalGabung: String, // YYYY-MM-DD
    val aktif: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "iuran_settings",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class IuranSettings(
    @PrimaryKey val organizationId: String,
    val namaIuran: String = "Iuran Bulanan",
    val nominal: Double = 0.0,
    val tanggalMulai: String, // YYYY-MM-DD
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "kategori",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("organizationId")]
)
data class Kategori(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val nama: String,
    val tipe: String, // "masuk" or "keluar"
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "transaksi",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Anggota::class,
            parentColumns = ["id"],
            childColumns = ["anggotaId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Akun::class,
            parentColumns = ["id"],
            childColumns = ["akunId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Akun::class,
            parentColumns = ["id"],
            childColumns = ["akunTujuanId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = User::class,
            parentColumns = ["id"],
            childColumns = ["createdBy"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("organizationId"),
        Index("tanggal"),
        Index("akunId"),
        Index("akunTujuanId"),
        Index("anggotaId"),
        Index("createdBy")
    ]
)
data class Transaksi(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val tipe: String, // "masuk", "keluar", "transfer", "penyesuaian"
    val sumber: String = "umum", // "umum", "iuran"
    val kategori: String? = null,
    val jumlah: Double,
    val catatan: String? = null,
    val metode: String? = "Tunai", // "Tunai", "Transfer Bank", "E-Wallet"
    val anggotaId: String? = null,
    val akunId: String? = null,
    val akunTujuanId: String? = null,
    val tanggal: String, // YYYY-MM-DD
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "iuran_pembayaran",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Anggota::class,
            parentColumns = ["id"],
            childColumns = ["anggotaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Transaksi::class,
            parentColumns = ["id"],
            childColumns = ["transaksiId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("organizationId"),
        Index("anggotaId"),
        Index(value = ["transaksiId"], unique = true)
    ]
)
data class IuranPembayaran(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val anggotaId: String,
    val jumlahTotal: Double,
    val tanggalBayar: String, // YYYY-MM-DD
    val transaksiId: String,
    val catatan: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "iuran_alokasi",
    foreignKeys = [
        ForeignKey(
            entity = Organization::class,
            parentColumns = ["id"],
            childColumns = ["organizationId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Anggota::class,
            parentColumns = ["id"],
            childColumns = ["anggotaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IuranPembayaran::class,
            parentColumns = ["id"],
            childColumns = ["pembayaranId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("organizationId"),
        Index("anggotaId"),
        Index("pembayaranId"),
        Index(value = ["anggotaId", "periode"])
    ]
)
data class IuranAlokasi(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val organizationId: String,
    val anggotaId: String,
    val pembayaranId: String,
    val periode: String, // "YYYY-MM"
    val jumlah: Double,
    val createdAt: Long = System.currentTimeMillis()
)
