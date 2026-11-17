package com.few.passend.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CredentialEntity::class, PasswordHistoryEntity::class],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun credentialDao(): CredentialDao
    abstract fun passwordHistoryDao(): PasswordHistoryDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE credentials ADD COLUMN encryptedOtpSecret TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE credentials ADD COLUMN otpIv TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE credentials ADD COLUMN encryptedCustomFields TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE credentials ADD COLUMN customFieldsIv TEXT NOT NULL DEFAULT ''")
            }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS password_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "credentialId INTEGER NOT NULL, " +
                    "encryptedPassword TEXT NOT NULL, " +
                    "iv TEXT NOT NULL, " +
                    "replacedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_password_history_credentialId ON password_history(credentialId)")
            }
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE credentials ADD COLUMN otpType TEXT NOT NULL DEFAULT 'TOTP'")
                db.execSQL("ALTER TABLE credentials ADD COLUMN otpCounter INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(ctx: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                ctx.applicationContext,
                AppDatabase::class.java,
                "passend.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build().also { INSTANCE = it }
        }

        fun resetInstance() {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Throwable) {}
                INSTANCE = null
            }
        }
    }
}