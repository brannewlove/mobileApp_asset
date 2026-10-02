package com.antigravity.assetmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.assetmanager.model.Asset
import com.antigravity.assetmanager.model.TradeLog
import com.antigravity.assetmanager.ui.components.GlassCard
import com.antigravity.assetmanager.ui.components.SearchBar
import com.antigravity.assetmanager.ui.theme.AppColors

data class AssetTradeGroup(
    val assetNo: String,
    val logs: List<TradeLog>, // 오래된 순서대로 정렬됨
    val firstDate: String,
    val latestDate: String
)

@Composable
fun TradeLogScreen(
    tradeLogs: List<TradeLog>,
    trackedAsset: Asset? = null,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 자산번호별로 그룹화 및 각 자산 내 로그는 "오래된 날짜부터(오름차순)" 정렬!
    val groupedList = remember(tradeLogs) {
        tradeLogs.groupBy { it.assetNo.trim() }
            .map { (assetNo, logs) ->
                // 오래된 날짜부터 오름차순 정렬 (과거 ➔ 최신)
                val sortedLogs = logs.sortedBy { it.dateStr }
                AssetTradeGroup(
                    assetNo = assetNo,
                    logs = sortedLogs,
                    firstDate = sortedLogs.firstOrNull()?.dateStr ?: "",
                    latestDate = sortedLogs.lastOrNull()?.dateStr ?: ""
                )
            }
            .sortedByDescending { it.latestDate }
    }

    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "자산번호, 사번, 사원명, 일자, 메모 검색..."
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 그룹화된 변경 이력 목록
            if (groupedList.isNotEmpty()) {
                items(groupedList, key = { it.assetNo }) { group ->
                    AssetTradeGroupCard(group = group)
                }
            } else if (trackedAsset != null) {
                // 추적 자산은 존재하지만 trade 시트에 이동 로그가 아직 없는 경우
                item(key = "no_logs_for_tracked") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1B1B30))
                            .border(1.dp, Color(0xFF2C2D4A), RoundedCornerShape(12.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = AppColors.Secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "자산 [${trackedAsset.assetNumber}]",
                                color = Color(0xFF8C82FF),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "현재 사용자 [${trackedAsset.userName.ifBlank { "사용자" }}] 배정 이후 추가적인 사용자 변경 및 이동 이력이 없습니다.",
                                color = AppColors.TextMuted,
                                fontSize = 12.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // 검색 결과도 없고 추적 자산도 없는 경우
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "검색된 자산 또는 변경 이력이 없습니다."
                            else "조회 가능한 자산 이동 및 사용자 변경 이력이 없습니다.",
                            color = AppColors.TextMuted,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 첨부 이미지 디자인과 100% 동일한 자산별 타임라인 변경 이력 카드
 */
@Composable
fun AssetTradeGroupCard(group: AssetTradeGroup) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1B1B30))
            .border(1.dp, Color(0xFF2C2D4A), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            // 헤더: [ 큐브아이콘  자산번호 ]      변경 N건
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x337065F0))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = Color(0xFF8C82FF),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = group.assetNo,
                            color = Color(0xFF8C82FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Text(
                    text = "변경 ${group.logs.size}건",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 타임라인 리스트
            Column {
                group.logs.forEachIndexed { index, log ->
                    val isLast = index == group.logs.size - 1

                    Row(modifier = Modifier.fillMaxWidth()) {
                        // 좌측: 타임라인 Dot & 연결 Line
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(5.dp))
                            // 보라색 원(Dot)
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF7065F0))
                            )
                            // 수직 연결선(Line)
                            if (!isLast) {
                                Box(
                                    modifier = Modifier
                                        .width(1.5.dp)
                                        .height(72.dp)
                                        .background(Color(0xFF2C2D4A))
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // 우측: 상세 이력 정보 (일시, 이전 사용자/부서 > 신규 사용자/부서)
                        Column(modifier = Modifier.weight(1f)) {
                            // 1. 일시
                            Text(
                                text = log.dateStr,
                                color = Color(0xFFCBD5E1),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // 2. '이전' & '신규' 라벨
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "이전",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = "신규",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // 3. 사용자명 이전 > 신규
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val exName = if (log.exUserName.isNotBlank()) log.exUserName else (log.exUserId.ifBlank { "없음" })
                                val newName = if (log.joinedName.isNotBlank()) log.joinedName else (log.cjId.ifBlank { "미지정" })

                                Text(
                                    text = exName,
                                    color = Color(0xFF8C82FF),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = newName,
                                    color = Color(0xFF8C82FF),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            // 4. 부서명 이전 / 신규
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = log.exUserPart.ifBlank { "-" },
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = log.joinedPart.ifBlank { "-" },
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            // 5. 메모
                            if (log.note.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "메모: ${log.note}",
                                    color = Color(0xFF64748B),
                                    fontSize = 11.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }
        }
    }
}
