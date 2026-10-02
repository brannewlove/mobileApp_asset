package com.antigravity.assetmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.antigravity.assetmanager.ui.components.AssetItemCard
import com.antigravity.assetmanager.ui.components.DepartmentFilter
import com.antigravity.assetmanager.ui.components.SearchBar
import com.antigravity.assetmanager.ui.theme.AppColors

@Composable
fun AssetListScreen(
    assets: List<Asset>,
    departments: List<String>,
    selectedDepartment: String,
    searchQuery: String,
    userStats: Map<String, Pair<Int, Int>> = emptyMap(),
    onSearchChange: (String) -> Unit,
    onDepartmentSelect: (String) -> Unit,
    onTrackClick: (String) -> Unit,
    onSearchUserClick: (String) -> Unit,
    onSearchDeptClick: (String) -> Unit,
    onCancelCheckClick: (String) -> Unit,
    onSaveNote: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchBar(
            query = searchQuery,
            onQueryChange = onSearchChange,
            placeholder = "자산번호, 사원명, 모델명 실시간 검색..."
        )

        DepartmentFilter(
            departments = departments,
            selectedDepartment = selectedDepartment,
            onSelectDepartment = onDepartmentSelect
        )

        // 미조사 자산이 상위에 오도록 정렬 (false: 미조사 ➔ true: 실사완료)
        val sortedAssets: List<Asset> = remember(assets) {
            assets.sortedWith(compareBy({ it.isChecked }, { it.assetNumber }))
        }

        // 미조사 자산의 분류별(category) 집계
        val uninspectedByCategory: Map<String, Int> = remember(assets) {
            assets.filter { !it.isChecked }
                .groupBy { it.category.ifBlank { "기타" } }
                .mapValues { it.value.size }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${assets.size}건",
                color = AppColors.TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            // 미조사 자산 분류별 숫자 표시 (가로 스크롤)
            if (uninspectedByCategory.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "미조사:",
                        color = AppColors.Warning,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    for ((category, count) in uninspectedByCategory) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.Warning.copy(alpha = 0.16f))
                                .border(1.dp, AppColors.Warning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$category $count",
                                color = AppColors.Warning,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else if (assets.isNotEmpty()) {
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00E676).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF00E676).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "전체 실사완료",
                        color = Color(0xFF00E676),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (assets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank() || selectedDepartment != "전체")
                        "검색 조건과 일치하는 자산이 없습니다."
                    else "등록된 자산 데이터가 없습니다. 상단 '동기화' 메뉴에서 마스터 시트를 동기화해주세요.",
                    color = AppColors.TextMuted,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = sortedAssets,
                    key = { it.assetNumber }
                ) { asset ->
                    AssetItemCard(
                        asset = asset,
                        userStats = userStats[asset.inUser.trim().ifBlank { asset.userName.trim() }],
                        onTrackClick = onTrackClick,
                        onSearchUserClick = onSearchUserClick,
                        onSearchDeptClick = onSearchDeptClick,
                        onCancelCheckClick = onCancelCheckClick,
                        onSaveNote = onSaveNote
                    )
                }
            }
        }
    }
}
