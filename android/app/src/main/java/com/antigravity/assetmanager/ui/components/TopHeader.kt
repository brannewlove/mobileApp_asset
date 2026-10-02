package com.antigravity.assetmanager.ui.components

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.assetmanager.model.InspectionSession
import com.antigravity.assetmanager.ui.theme.AppColors

@Composable
fun TopHeader(
    totalCount: Int,
    checkedCount: Int,
    activeSession: InspectionSession?,
    sessions: List<InspectionSession>,
    onCreateSession: (String) -> Unit,
    onSwitchSession: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSessionDialog by remember { mutableStateOf(false) }

    val progress = if (totalCount > 0) checkedCount.toFloat() / totalCount.toFloat() else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    val percent = (progress * 100).toInt()

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        cornerRadius = 12.dp,
        contentPadding = 8.dp
    ) {
        Column {
            // 회차 상태 및 변경 버튼 바
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.BgInput)
                        .border(1.dp, AppColors.BorderGlass, RoundedCornerShape(6.dp))
                        .clickable { showSessionDialog = true }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "회차: ${activeSession?.sessionName ?: "기본 회차"}",
                        color = AppColors.TextMain,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = "회차 전환",
                        tint = AppColors.Secondary,
                        modifier = Modifier.height(14.dp).width(14.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$percent%",
                        color = AppColors.Secondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " ($checkedCount/$totalCount)",
                        color = AppColors.TextMuted,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 슬림 진행률 게이지 바
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AppColors.Secondary,
                trackColor = AppColors.BgDark
            )
        }
    }

    if (showSessionDialog) {
        SessionManagementDialog(
            sessions = sessions,
            activeSession = activeSession,
            onDismiss = { showSessionDialog = false },
            onCreateSession = {
                onCreateSession(it)
                showSessionDialog = false
            },
            onSelectSession = {
                onSwitchSession(it)
                showSessionDialog = false
            }
        )
    }
}

@Composable
fun SessionManagementDialog(
    sessions: List<InspectionSession>,
    activeSession: InspectionSession?,
    onDismiss: () -> Unit,
    onCreateSession: (String) -> Unit,
    onSelectSession: (Long) -> Unit
) {
    var newSessionName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.BgCardSolid,
        title = {
            Text(
                text = "실사 회차 선택 및 생성",
                color = AppColors.TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "새로운 회차를 생성하면 실사 데이터가 분리 저장됩니다.",
                    color = AppColors.TextMuted,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 신규 회차 입력창
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newSessionName,
                        onValueChange = { newSessionName = it },
                        placeholder = { Text("예: 2026년 2차 실사", color = AppColors.TextMuted, fontSize = 13.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.Primary,
                            unfocusedBorderColor = AppColors.BorderGlass,
                            focusedTextColor = AppColors.TextMain,
                            unfocusedTextColor = AppColors.TextMain
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = {
                            if (newSessionName.isNotBlank()) {
                                onCreateSession(newSessionName.trim())
                                newSessionName = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("생성", color = AppColors.TextMain, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "등록된 회차 목록 (${sessions.size}개)",
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(modifier = Modifier.height(180.dp)) {
                    items(sessions, key = { it.id }) { session ->
                        val isCurrent = session.id == activeSession?.id
                        val borderColor = if (isCurrent) AppColors.Secondary else AppColors.BorderGlass
                        val bgColor = if (isCurrent) AppColors.Secondary.copy(alpha = 0.12f) else AppColors.BgInput

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(bgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .clickable { onSelectSession(session.id) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = session.sessionName,
                                    color = AppColors.TextMain,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "생성: ${session.createdAt}",
                                    color = AppColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            }

                            if (isCurrent) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "선택됨",
                                        tint = AppColors.Secondary,
                                        modifier = Modifier.height(16.dp).width(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("진행중", color = AppColors.Secondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("닫기", color = AppColors.TextMuted)
            }
        }
    )
}
