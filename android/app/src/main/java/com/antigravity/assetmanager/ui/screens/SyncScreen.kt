package com.antigravity.assetmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.assetmanager.ui.components.GlassCard
import com.antigravity.assetmanager.ui.theme.AppColors

@Composable
fun SyncScreen(
    isSyncing: Boolean,
    totalAssets: Int,
    checkedAssets: Int,
    userEmail: String?,
    onGoogleSignInClick: () -> Unit,
    onGoogleSignOutClick: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "데이터 동기화 및 설정",
            color = AppColors.TextMain,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        // 1. Google 계정 로그인 상태 카드
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = if (userEmail != null) AppColors.Secondary else AppColors.Warning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (userEmail != null) "구글 계정 연결됨" else "구글 계정 미연결",
                            color = AppColors.TextMain,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    if (userEmail != null) {
                        OutlinedButton(
                            onClick = onGoogleSignOutClick,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("로그아웃", color = AppColors.TextMuted, fontSize = 11.sp)
                        }
                    }
                }

                if (userEmail != null) {
                    Text(
                        text = "로그인 계정: $userEmail",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp
                    )
                } else {
                    Text(
                        text = "Google Drive의 마스터 시트를 불러오려면 구글 계정 인증이 필요합니다.",
                        color = AppColors.TextMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = onGoogleSignInClick,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google 계정으로 로그인", color = AppColors.TextMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        // 2. 현재 로컬 Room DB 현황 카드
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "현재 로컬 데이터베이스 현황",
                    color = AppColors.TextMain,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "총 등록 자산:", color = AppColors.TextMuted, fontSize = 13.sp)
                    Text(text = "$totalAssets 건", color = AppColors.TextMain, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "실사 완료 자산:", color = AppColors.TextMuted, fontSize = 13.sp)
                    Text(text = "$checkedAssets 건", color = AppColors.Secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 3. 구글 시트 마스터 데이터 동기화 버튼
        Button(
            onClick = onSyncClick,
            enabled = !isSyncing,
            colors = ButtonDefaults.buttonColors(
                containerColor = AppColors.Primary,
                disabledContainerColor = AppColors.Primary.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            if (isSyncing) {
                CircularProgressIndicator(
                    color = AppColors.TextMain,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(text = "동기화 진행 중...", color = AppColors.TextMain, fontSize = 14.sp)
            } else {
                Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, tint = AppColors.TextMain)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "마스터 시트 최신 데이터 동기화",
                    color = AppColors.TextMain,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
