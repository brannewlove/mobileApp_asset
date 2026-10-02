package com.antigravity.assetmanager.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 실사 회차(Session) 엔티티
 * 회차별로 실사 데이터를 분리하여 보관 및 관리합니다.
 */
@Entity(
    tableName = "inspection_sessions",
    indices = [Index(value = ["sessionName"], unique = true)]
)
data class InspectionSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionName: String,
    val createdAt: String,
    val isActive: Boolean = false,
    val totalCount: Int = 0,
    val checkedCount: Int = 0
)

/**
 * 특정 회차에서 실사된 자산 매핑 엔티티
 */
@Entity(
    tableName = "session_scans",
    primaryKeys = ["sessionId", "assetNumber"],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["assetNumber"])
    ]
)
data class SessionScan(
    val sessionId: Long,
    val assetNumber: String,
    val inspectionTime: String,
    val note: String = ""
)
