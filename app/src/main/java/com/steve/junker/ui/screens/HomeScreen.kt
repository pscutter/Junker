package com.steve.junker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.steve.junker.ui.viewmodel.GameViewModel
import com.steve.junker.DrawerHeaderBar

@Composable
fun HomeScreen(
    viewModel: GameViewModel,
    onMenuClick: () -> Unit,
    onNewRoundClick: () -> Unit,
    onViewPreviousClick: () -> Unit
) {
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val appBg = getAppBg(isDarkMode)
    val textSecondary = getTextSecondary(isDarkMode)

    Surface(modifier = Modifier.fillMaxSize(), color = appBg) {
        Column(modifier = Modifier.padding(24.dp)) {
            DrawerHeaderBar(
                title = "Dashboard", 
                isDarkMode = isDarkMode, 
                onMenuClick = onMenuClick,
                onThemeToggle = { viewModel.toggleTheme() }
            )
            
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "JUNKER", 
                    fontSize = 38.sp, 
                    fontWeight = FontWeight.ExtraBold, 
                    color = GolfGreen, 
                    letterSpacing = 2.sp
                )
                Text(
                    text = "Side-Action Match Matrix", 
                    fontSize = 14.sp, 
                    color = textSecondary, 
                    modifier = Modifier.padding(bottom = 40.dp)
                )

                Button(
                    onClick = onNewRoundClick,
                    colors = ButtonDefaults.buttonColors(containerColor = GolfGreen),
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp)
                ) {
                    Text("New Round ⛳", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedButton(
                    onClick = onViewPreviousClick,
                    border = androidx.compose.foundation.BorderStroke(2.dp, GolfGreen),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = GolfGreen),
                    modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 16.dp)
                ) {
                    Text("View Previous Rounds 🗓️", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = com.steve.junker.R.drawable.prestige_logo),
                    contentDescription = "Prestige Worldwide Logo",
                    modifier = Modifier.size(120.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "© 2026 Prestige Worldwide. All rights reserved.",
                    fontSize = 11.sp,
                    color = textSecondary,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}