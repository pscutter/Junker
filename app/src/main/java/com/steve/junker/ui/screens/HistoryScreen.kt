package com.steve.junker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steve.junker.ui.viewmodel.GameViewModel
import com.steve.junker.DrawerHeaderBar
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun MatchHistoryScreen(
    viewModel: GameViewModel, 
    onMenuClick: () -> Unit,
    onBack: () -> Unit,
    onResumeRound: () -> Unit
) {
    val savedRounds by viewModel.savedRounds.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    
    val appBg = getAppBg(isDarkMode)
    val cardBg = getCardBg(isDarkMode)
    val textPrimary = getTextPrimary(isDarkMode)
    val textSecondary = getTextSecondary(isDarkMode)
    val goldColor = getGoldThemeColor(isDarkMode)
    
    val dateFormatter = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())

    Surface(modifier = Modifier.fillMaxSize(), color = appBg) {
        Column(modifier = Modifier.padding(16.dp)) {
            DrawerHeaderBar("Past Rounds", isDarkMode, onMenuClick, null)

            Spacer(modifier = Modifier.height(12.dp))

            if (savedRounds.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No saved matches found.", fontSize = 18.sp, color = textSecondary, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(savedRounds) { round ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.restoreRoundFromHistory(round.id, round.courseName) {
                                        onResumeRound()
                                    }
                                }
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(round.courseName, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                                        Text(dateFormatter.format(round.dateLong), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = goldColor)
                                    }
                                    IconButton(
                                        onClick = { viewModel.deleteRoundFromHistory(round) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Text("✕", color = Color.Red, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = textSecondary.copy(alpha = 0.3f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val isTeamRound = round.playerNamesWithDots.contains("team_") || round.playerNamesWithDots.contains("Team ") || round.playerNamesWithDots.contains(" / ")
                                Text(
                                    text = if (isTeamRound) "Final Team Standings:" else "Final Player Standings:", 
                                    fontSize = 15.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = textSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = round.playerNamesWithDots, 
                                    fontSize = 18.sp, 
                                    fontWeight = FontWeight.Bold, 
                                    color = textPrimary, 
                                    lineHeight = 24.sp
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .background(GolfGreen.copy(alpha = 0.15f))
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("📝 Tap to Resume / Edit Round", color = GolfGreen, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}