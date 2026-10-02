package com.antigravity.assetmanager.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 사원/사용자 엔티티 (Domain Model & Room Entity)
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["cjId"], unique = true),
        Index(value = ["userName"]),
        Index(value = ["department"])
    ]
)
data class User(
    @PrimaryKey
    val cjId: String,
    val userName: String = "",
    val department: String = "",
    val position: String = ""
)
