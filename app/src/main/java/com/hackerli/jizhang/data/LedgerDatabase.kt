package com.hackerli.jizhang.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class LedgerDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE quick_tags (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE
                    CHECK(length(trim(name)) BETWEEN 1 AND 8),
                emoji TEXT NOT NULL CHECK(length(trim(emoji)) > 0),
                image_path TEXT CHECK(image_path IS NULL OR length(image_path) > 0),
                color_argb INTEGER NOT NULL,
                sort_order INTEGER NOT NULL CHECK(sort_order >= 0),
                is_archived INTEGER NOT NULL DEFAULT 0 CHECK(is_archived IN (0, 1))
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                amount_cents INTEGER NOT NULL CHECK(amount_cents > 0 AND amount_cents % 100 = 0),
                tag_id INTEGER NOT NULL,
                occurred_at INTEGER NOT NULL CHECK(occurred_at > 0),
                note TEXT NOT NULL DEFAULT '',
                latitude REAL NOT NULL CHECK(latitude BETWEEN -90.0 AND 90.0),
                longitude REAL NOT NULL CHECK(longitude BETWEEN -180.0 AND 180.0),
                location_accuracy_m REAL NOT NULL CHECK(location_accuracy_m >= 0),
                location_label TEXT NOT NULL,
                FOREIGN KEY(tag_id) REFERENCES quick_tags(id) ON DELETE RESTRICT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE refunds (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                expense_id INTEGER NOT NULL,
                amount_cents INTEGER NOT NULL CHECK(amount_cents > 0 AND amount_cents % 100 = 0),
                occurred_at INTEGER NOT NULL CHECK(occurred_at > 0),
                FOREIGN KEY(expense_id) REFERENCES expenses(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE expense_photos (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                expense_id INTEGER NOT NULL,
                path TEXT NOT NULL UNIQUE CHECK(length(path) > 0),
                sort_order INTEGER NOT NULL CHECK(sort_order >= 0),
                FOREIGN KEY(expense_id) REFERENCES expenses(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_expenses_occurred_at ON expenses(occurred_at DESC)")
        db.execSQL("CREATE INDEX idx_refunds_expense ON refunds(expense_id, occurred_at)")
        db.execSQL("CREATE INDEX idx_photos_expense ON expense_photos(expense_id, sort_order)")
        insertDefaultTags(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE quick_tags ADD COLUMN image_path TEXT")
        }
    }

    fun getTags(includeArchived: Boolean = false): List<QuickTag> {
        val result = mutableListOf<QuickTag>()
        readableDatabase.query(
            "quick_tags",
            arrayOf("id", "name", "emoji", "image_path", "color_argb", "sort_order", "is_archived"),
            if (includeArchived) null else "is_archived = 0",
            null,
            null,
            null,
            "is_archived ASC, sort_order ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += QuickTag(
                    id = cursor.getLong(0),
                    name = cursor.getString(1),
                    emoji = cursor.getString(2),
                    imagePath = cursor.getString(3),
                    colorArgb = cursor.getInt(4),
                    sortOrder = cursor.getInt(5),
                    isArchived = cursor.getInt(6) != 0,
                )
            }
        }
        return result
    }

    fun getExpenses(): List<Expense> {
        val db = readableDatabase
        return db.inTransaction {
            val photos = getPhotos(db).groupBy { it.expenseId }
            val refunds = getRefunds(db).groupBy { it.expenseId }
            val result = mutableListOf<Expense>()
            db.rawQuery(
                """
                SELECT e.id, e.amount_cents, e.tag_id, t.name, t.emoji, t.image_path, t.color_argb,
                       e.occurred_at, e.note, e.latitude, e.longitude,
                       e.location_accuracy_m, e.location_label
                FROM expenses e
                INNER JOIN quick_tags t ON t.id = e.tag_id
                ORDER BY e.occurred_at DESC, e.id DESC
                """.trimIndent(),
                null,
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    result += Expense(
                        id = id,
                        amountCents = cursor.getLong(1),
                        tagId = cursor.getLong(2),
                        tagName = cursor.getString(3),
                        tagEmoji = cursor.getString(4),
                        tagImagePath = cursor.getString(5),
                        tagColorArgb = cursor.getInt(6),
                        occurredAt = cursor.getLong(7),
                        note = cursor.getString(8),
                        latitude = cursor.getDouble(9),
                        longitude = cursor.getDouble(10),
                        locationAccuracyMeters = cursor.getFloat(11),
                        locationLabel = cursor.getString(12),
                        photos = photos[id].orEmpty(),
                        refunds = refunds[id].orEmpty(),
                    )
                }
            }
            result
        }
    }

    fun insertExpense(
        amountCents: Long,
        tagId: Long,
        occurredAt: Long,
        note: String,
        location: LocationSnapshot,
        photoPaths: List<String>,
    ): Long = writableDatabase.inTransaction {
        val expenseId = insertOrThrow(
            "expenses",
            null,
            ContentValues().apply {
                put("amount_cents", amountCents)
                put("tag_id", tagId)
                put("occurred_at", occurredAt)
                put("note", note)
                put("latitude", location.latitude)
                put("longitude", location.longitude)
                put("location_accuracy_m", location.accuracyMeters)
                put("location_label", location.label)
            },
        )
        insertPhotos(this, expenseId, photoPaths)
        expenseId
    }

    fun updateExpense(
        expenseId: Long,
        amountCents: Long,
        tagId: Long,
        note: String,
        photoPaths: List<String>,
    ) = writableDatabase.inTransaction {
        val refundedCents = rawQuery(
            "SELECT COALESCE(SUM(amount_cents), 0) FROM refunds WHERE expense_id = ?",
            arrayOf(expenseId.toString()),
        ).use { cursor ->
            check(cursor.moveToFirst())
            cursor.getLong(0)
        }
        require(amountCents >= refundedCents) { "原消费金额不能低于累计退款" }
        val updated = update(
            "expenses",
            ContentValues().apply {
                put("amount_cents", amountCents)
                put("tag_id", tagId)
                put("note", note)
            },
            "id = ?",
            arrayOf(expenseId.toString()),
        )
        require(updated == 1) { "账单不存在" }
        delete("expense_photos", "expense_id = ?", arrayOf(expenseId.toString()))
        insertPhotos(this, expenseId, photoPaths)
    }

    fun deleteExpense(id: Long) {
        require(writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString())) == 1) {
            "账单不存在"
        }
    }

    fun insertRefund(expenseId: Long, amountCents: Long, occurredAt: Long): Long =
        writableDatabase.inTransaction {
            val remainingCents = rawQuery(
                """
                SELECT e.amount_cents - COALESCE(SUM(r.amount_cents), 0)
                FROM expenses e LEFT JOIN refunds r ON r.expense_id = e.id
                WHERE e.id = ? GROUP BY e.id
                """.trimIndent(),
                arrayOf(expenseId.toString()),
            ).use { cursor ->
                require(cursor.moveToFirst()) { "账单不存在" }
                cursor.getLong(0)
            }
            require(amountCents in 1L..remainingCents) { "退款总额不能超过原消费金额" }
            insertOrThrow(
                "refunds",
                null,
                ContentValues().apply {
                    put("expense_id", expenseId)
                    put("amount_cents", amountCents)
                    put("occurred_at", occurredAt)
                },
            )
        }

    fun deleteRefund(id: Long) {
        require(writableDatabase.delete("refunds", "id = ?", arrayOf(id.toString())) == 1) {
            "退款记录不存在"
        }
    }

    fun insertTag(name: String, emoji: String, imagePath: String?, colorArgb: Int, sortOrder: Int) {
        writableDatabase.insertOrThrow(
            "quick_tags",
            null,
            ContentValues().apply {
                put("name", name)
                put("emoji", emoji)
                put("image_path", imagePath)
                put("color_argb", colorArgb)
                put("sort_order", sortOrder)
            },
        )
    }

    fun updateTag(tag: QuickTag) {
        require(
            writableDatabase.update(
                "quick_tags",
                ContentValues().apply {
                    put("name", tag.name)
                    put("emoji", tag.emoji)
                    put("image_path", tag.imagePath)
                    put("color_argb", tag.colorArgb)
                },
                "id = ?",
                arrayOf(tag.id.toString()),
            ) == 1,
        ) { "标签不存在" }
    }

    fun setTagArchived(id: Long, archived: Boolean) = writableDatabase.inTransaction {
        if (archived) {
            val activeCount = rawQuery(
                "SELECT COUNT(*) FROM quick_tags WHERE is_archived = 0",
                null,
            ).use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
            require(activeCount > 1) { "至少保留一个有效标签" }
        }
        require(
            update(
                "quick_tags",
                ContentValues().apply { put("is_archived", if (archived) 1 else 0) },
                "id = ?",
                arrayOf(id.toString()),
            ) == 1,
        ) { "标签不存在" }
    }

    fun updateTagOrder(tags: List<QuickTag>) = writableDatabase.inTransaction {
        tags.forEachIndexed { index, tag ->
            require(
                update(
                    "quick_tags",
                    ContentValues().apply { put("sort_order", index) },
                    "id = ? AND is_archived = 0",
                    arrayOf(tag.id.toString()),
                ) == 1,
            ) { "标签不存在" }
        }
    }

    private fun getPhotos(db: SQLiteDatabase): List<ExpensePhoto> {
        val result = mutableListOf<ExpensePhoto>()
        db.query(
            "expense_photos",
            arrayOf("id", "expense_id", "path", "sort_order"),
            null,
            null,
            null,
            null,
            "expense_id ASC, sort_order ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += ExpensePhoto(
                    id = cursor.getLong(0),
                    expenseId = cursor.getLong(1),
                    path = cursor.getString(2),
                    sortOrder = cursor.getInt(3),
                )
            }
        }
        return result
    }

    private fun getRefunds(db: SQLiteDatabase): List<Refund> {
        val result = mutableListOf<Refund>()
        db.query(
            "refunds",
            arrayOf("id", "expense_id", "amount_cents", "occurred_at"),
            null,
            null,
            null,
            null,
            "occurred_at ASC, id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Refund(
                    id = cursor.getLong(0),
                    expenseId = cursor.getLong(1),
                    amountCents = cursor.getLong(2),
                    occurredAt = cursor.getLong(3),
                )
            }
        }
        return result
    }

    private fun insertPhotos(db: SQLiteDatabase, expenseId: Long, paths: List<String>) {
        paths.distinct().forEachIndexed { index, path ->
            db.insertOrThrow(
                "expense_photos",
                null,
                ContentValues().apply {
                    put("expense_id", expenseId)
                    put("path", path)
                    put("sort_order", index)
                },
            )
        }
    }

    private fun insertDefaultTags(db: SQLiteDatabase) {
        val defaults = listOf(
            Triple("麦当劳", "🍔", TagPalette.colors[0]),
            Triple("肯德基", "🍗", TagPalette.colors[1]),
            Triple("华莱士", "🍔", TagPalette.colors[2]),
            Triple("打车", "🚕", TagPalette.colors[3]),
            Triple("公交", "🚌", TagPalette.colors[4]),
            Triple("地铁", "🚇", TagPalette.colors[5]),
        )
        defaults.forEachIndexed { index, (name, emoji, color) ->
            db.insertOrThrow(
                "quick_tags",
                null,
                ContentValues().apply {
                    put("name", name)
                    put("emoji", emoji)
                    put("color_argb", color)
                    put("sort_order", index)
                },
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "jideji.db"
        private const val DATABASE_VERSION = 2
    }
}

private inline fun <T> SQLiteDatabase.inTransaction(block: SQLiteDatabase.() -> T): T {
    beginTransaction()
    return try {
        val result = block()
        setTransactionSuccessful()
        result
    } finally {
        endTransaction()
    }
}
