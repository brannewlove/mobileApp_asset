package com.antigravity.assetmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.assetmanager.ui.theme.AppColors

@Composable
fun DepartmentFilter(
    departments: List<String>,
    selectedDepartment: String,
    onSelectDepartment: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isModalOpen by remember { mutableStateOf(false) }
    var deptSearchQuery by remember { mutableStateOf("") }

    val allDepartments = remember(departments) {
        listOf("전체") + departments.filter { it.isNotBlank() && it != "전체" }.sorted()
    }

    val filteredList = remember(allDepartments, deptSearchQuery) {
        if (deptSearchQuery.isBlank()) allDepartments
        else allDepartments.filter { it.contains(deptSearchQuery.trim(), ignoreCase = true) }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 부서 선택 모달 열기 버튼
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF22233D))
                .border(1.dp, if (selectedDepartment != "전체") AppColors.Primary else Color(0xFF33355A), RoundedCornerShape(10.dp))
                .clickable {
                    deptSearchQuery = ""
                    isModalOpen = true
                }
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterAlt,
                        contentDescription = null,
                        tint = if (selectedDepartment != "전체") AppColors.Secondary else AppColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (selectedDepartment == "전체") "부서: 전체 (모든 부서)" else "부서: $selectedDepartment",
                        color = if (selectedDepartment != "전체") AppColors.TextMain else AppColors.TextMuted,
                        fontSize = 13.sp,
                        fontWeight = if (selectedDepartment != "전체") FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = AppColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // 전체가 아닌 부서 선택 시 빠른 초기화 버튼
        if (selectedDepartment != "전체") {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF22233D))
                    .border(1.dp, Color(0xFF33355A), RoundedCornerShape(10.dp))
                    .clickable { onSelectDepartment("전체") }
                    .padding(horizontal = 10.dp, vertical = 9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "초기화",
                        tint = AppColors.TextMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("초기화", color = AppColors.TextMuted, fontSize = 12.sp)
                }
            }
        }
    }

    // 부서 선택 모달창 (다이얼로그)
    if (isModalOpen) {
        AlertDialog(
            onDismissRequest = { isModalOpen = false },
            containerColor = Color(0xFF1E1E34),
            title = {
                Text(
                    text = "부서 선택 (${allDepartments.size - 1}개 부서)",
                    color = AppColors.TextMain,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 모달 내부 부서 빠른 검색창
                    OutlinedTextField(
                        value = deptSearchQuery,
                        onValueChange = { deptSearchQuery = it },
                        placeholder = { Text("부서명 검색...", color = AppColors.TextMuted, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = AppColors.TextMuted, modifier = Modifier.size(16.dp))
                        },
                        trailingIcon = {
                            if (deptSearchQuery.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "검색어 지우기",
                                    tint = AppColors.TextMuted,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { deptSearchQuery = "" }
                                )
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = Color(0xFF33355A),
                            focusedTextColor = AppColors.TextMain,
                            unfocusedTextColor = AppColors.TextMain
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 부서 리스트
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                    ) {
                        items(filteredList) { dept ->
                            val isSelected = dept == selectedDepartment
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) AppColors.Primary.copy(alpha = 0.25f) else Color.Transparent)
                                    .clickable {
                                        onSelectDepartment(dept)
                                        isModalOpen = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (dept == "전체") "전체 (모든 부서)" else dept,
                                        color = if (isSelected) AppColors.Secondary else AppColors.TextMain,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = AppColors.Secondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { isModalOpen = false }) {
                    Text("닫기", color = AppColors.TextMuted)
                }
            }
        )
    }
}
