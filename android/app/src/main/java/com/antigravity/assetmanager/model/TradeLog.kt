package com.antigravity.assetmanager.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 자산 이동/불출/반납 이력 로그 엔티티
 */
@Entity(
    tableName = "trade_logs",
    indices = [
        Index(value = ["assetNo"]),
        Index(value = ["cjId"]),
        Index(value = ["dateStr"])
    ]
)
data class TradeLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val assetNo: String,
    val cjId: String = "",
    val dateStr: String = "",
    val exUserId: String = "",
    val note: String = "",
    // 보조/표시용 결합 필드
    val exUserName: String = "",
    val exUserPart: String = "",
    val joinedName: String = "",
    val joinedPart: String = ""
)
