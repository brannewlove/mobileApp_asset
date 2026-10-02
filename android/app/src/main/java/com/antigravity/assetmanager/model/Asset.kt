package com.antigravity.assetmanager.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 자산 엔티티 (Domain Model & Room Entity)
 * 수천 건 이상의 데이터 검색 및 필터링 속도 최적화를 위해 주요 검색 필드에 인덱스를 부여합니다.
 */
@Entity(
    tableName = "assets",
    indices = [
        Index(value = ["assetNumber"], unique = true),
        Index(value = ["department"]),
        Index(value = ["userName"]),
        Index(value = ["modelName"]),
        Index(value = ["status"])
    ]
)
data class Asset(
    @PrimaryKey
    val assetNumber: String,
    val modelName: String = "",
    val serialNumber: String = "",
    val category: String = "",
    val inUser: String = "",
    val userName: String = "",
    val department: String = "",
    val status: String = "pending", // "pending" or "checked"
    val inspectionTime: String = "",
    val note: String = "",
    val state: String = "" // "normal", "termination" 등
) {
    val isChecked: Boolean
        get() = status.equals("checked", ignoreCase = true)

    fun matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val q = query.trim().lowercase()
        return assetNumber.lowercase().contains(q) ||
                userName.lowercase().contains(q) ||
                inUser.lowercase().contains(q) ||
                modelName.lowercase().contains(q) ||
                serialNumber.lowercase().contains(q) ||
                department.lowercase().contains(q)
    }
}
