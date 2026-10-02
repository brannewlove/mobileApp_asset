package com.antigravity.assetmanager.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.assetmanager.model.Asset

@Composable
fun AssetItemCard(
    asset: Asset,
    userStats: Pair<Int, Int>? = null, // (done, total) -> 숫자만 표시 (예: 2/13)
    onTrackClick: (String) -> Unit = {},
    onSearchUserClick: (String) -> Unit = {},
    onSearchDeptClick: (String) -> Unit = {},
    onCancelCheckClick: (String) -> Unit = {},
    onSaveNote: ((assetNumber: String, note: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isChecked = asset.isChecked
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var noteInput by remember(asset.note) { mutableStateOf(asset.note) }
    var showCancelDialog by remember { mutableStateOf(false) }

    // 이미지 기반 컬러 팔레트
    val cardBg = Color(0xFF1B1B30)
    val cardBorder = if (isChecked) Color(0xFF10B981).copy(alpha = 0.5f) else Color(0xFF2C2D4A)
    val highlightGreen = Color(0xFF00E676)
    val purplePrimary = Color(0xFF7065F0)
    val purpleTrackBtnBg = Color(0xFF2D295C)
    val purpleTrackBtnText = Color(0xFF8C82FF)
    val textMain = Color(0xFFE2E8F0)
    val textMuted = Color(0xFF8E95A5)
    val badgeBg = Color(0xFF282844)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // 1. 실사완료 시 좌측 하이라이트 바
            if (isChecked) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(highlightGreen)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                // 1. 카드 헤더: 자산번호 + 사용자 통계 숫자(2/13) + 상태 아이콘
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = asset.assetNumber,
                        color = purplePrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 동일 사용자의 실사 현황: 숫자만 표시 (예: 2/13)
                        if (userStats != null && userStats.second > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(badgeBg)
                                    .border(1.dp, Color(0xFF3B3C60), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${userStats.first}/${userStats.second}",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // 실사완료 / 대기 상태 아이콘
                        if (isChecked) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "실사완료",
                                tint = highlightGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.RadioButtonUnchecked,
                                contentDescription = "대기",
                                tint = Color(0xFF5A5D7A),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 사용자 정보 & 보라색 추적 버튼
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
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val userDisplayName = asset.userName.ifBlank { "미지정" }
                        val suffix = if (asset.userName.isNotBlank() && !asset.userName.endsWith("님")) "님" else ""
                        Text(
                            text = "$userDisplayName$suffix (${asset.department.ifBlank { "부서미정" }})",
                            color = textMain,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // 보라색 '추적' 버튼 (실사완료의 초록색과 뚜렷이 구분)
                    Button(
                        onClick = { onTrackClick(asset.assetNumber) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = purpleTrackBtnBg,
                            contentColor = purpleTrackBtnText
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, purpleTrackBtnText.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text(
                            text = "추적",
                            color = purpleTrackBtnText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 3. 사번(ID) 행
                if (asset.inUser.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = textMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ID: ${asset.inUser}",
                            color = textMuted,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 4. 분류 | 모델명 행
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = asset.category.ifBlank { "일반" },
                        color = textMuted,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "|", color = Color(0xFF434568), fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = asset.modelName.ifBlank { "-" },
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 5. 시리얼넘버(SN) 행
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tag,
                        contentDescription = null,
                        tint = textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SN: ${asset.serialNumber.ifBlank { "-" }}",
                        color = textMuted,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 6. 조사 메모 행 (모달 없이 카드에서 바로 인라인 작성)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF22233D))
                        .border(1.dp, Color(0xFF33355A), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = if (noteInput.isNotBlank()) highlightGreen else textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = noteInput,
                        onValueChange = {
                            noteInput = it
                            onSaveNote?.invoke(asset.assetNumber, it)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color(0xFFCBD5E1),
                            fontSize = 12.sp
                        ),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(purplePrimary),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                        ),
                        decorationBox = { innerTextField ->
                            if (noteInput.isEmpty()) {
                                Text(
                                    text = "조사 메모",
                                    color = Color(0xFF6B7280),
                                    fontSize = 12.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 7. 하단 숏컷 액션 버튼 3종 (ID로 검색, 부서로 검색, 취소)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ID로 검색
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF22233D))
                            .border(1.dp, Color(0xFF33355A), RoundedCornerShape(8.dp))
                            .clickable { onSearchUserClick(asset.inUser) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ID로 검색", color = Color(0xFFCBD5E1), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    // 부서로 검색
                    Box(
                        modifier = Modifier
                            .weight(1.1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF22233D))
                            .border(1.dp, Color(0xFF33355A), RoundedCornerShape(8.dp))
                            .clickable { onSearchDeptClick(asset.department) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FilterAlt,
                                contentDescription = null,
                                tint = textMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("부서로 검색", color = Color(0xFFCBD5E1), fontSize = 12.sp)
                        }
                    }

                    // 취소 버튼 (실사 완료 시 표시)
                    if (isChecked) {
                        Box(
                            modifier = Modifier
                                .weight(0.9f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x22EF4444))
                                .border(1.dp, Color(0x55EF4444), RoundedCornerShape(8.dp))
                                .clickable { showCancelDialog = true }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color(0xFFF87171),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("취소", color = Color(0xFFF87171), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            containerColor = Color(0xFF1B1B30),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "실사 취소 확인",
                    color = textMain,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "자산 [${asset.assetNumber}]의 실사 완료 상태를 취소하시겠습니까?\n\n(실사 상태가 미실사로 변경됩니다)",
                    color = textMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCancelCheckClick(asset.assetNumber)
                        showCancelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("실사 취소", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("닫기", color = textMuted)
                }
            }
        )
    }
}
