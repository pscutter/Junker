package com.steve.junker

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn // 🟢 ADDED IMPORT
import androidx.compose.foundation.lazy.items // 🟢 ADDED IMPORT
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import com.steve.junker.data.db.JunkerDatabase
import com.steve.junker.ui.screens.*
import com.steve.junker.ui.viewmodel.GameViewModel
import com.steve.junker.data.models.HoleScore
import com.steve.junker.data.models.Course
import com.steve.junker.data.models.HoleCalculationResult
import com.steve.junker.data.models.calculateJunkDots

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = JunkerDatabase.getDatabase(this)
        
        gameViewModel.initDatabase(database.playerDao(), database.courseDao())
        gameViewModel.initRoundDatabase(database.gameRoundDao())

        setContent {
            MaterialTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val isDarkMode by gameViewModel.isDarkMode.collectAsState()
                
                val appBg = getAppBg(isDarkMode)
                val textPrimary = getTextPrimary(isDarkMode)

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        // 🟢 FIX: Replaced parameter name with standard background modifier for cross-version compatibility
                        ModalDrawerSheet(
                            modifier = Modifier.background(if (isDarkMode) Color(0xFF1E1E1E) else Color.White)
                        ) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "RAW JUNKER", 
                                modifier = Modifier.padding(16.dp), 
                                fontSize = 24.sp, 
                                fontWeight = FontWeight.ExtraBold,
                                color = GolfGreen
                            )
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            NavigationDrawerItem(
                                label = { Text("Home", color = textPrimary) },
                                selected = false,
                                onClick = { 
                                    scope.launch { 
                                        drawerState.close()
                                        navController.navigate("home") { popUpTo("home") { inclusive = true } } 
                                    } 
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("New Round Setup", color = textPrimary) },
                                selected = false,
                                onClick = { 
                                    scope.launch { 
                                        drawerState.close()
                                        navController.navigate("player_setup") 
                                    } 
                                }
                            )
                            NavigationDrawerItem(
                                label = { Text("View Previous Rounds", color = textPrimary) },
                                selected = false,
                                onClick = { 
                                    scope.launch { 
                                        drawerState.close()
                                        navController.navigate("history") 
                                    } 
                                }
                            )
                        }
                    }
                ) {
                    NavHost(navController = navController, startDestination = "launch") {
                        composable("launch") { JunkerLaunchScreen { navController.navigate("home") { popUpTo("launch") { inclusive = true } } } }
                        
                        composable("home") { 
                            HomeScreen(
                                viewModel = gameViewModel,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onNewRoundClick = { navController.navigate("player_setup") },
                                onViewPreviousClick = { navController.navigate("history") }
                            ) 
                        }
                        
                        composable("player_setup") { 
                            PlayerSetupScreen(
                                viewModel = gameViewModel, 
                                onMenuClick = { scope.launch { drawerState.open() } }
                            ) { 
                                navController.navigate("course_setup") 
                            } 
                        }
                        
                        composable("course_setup") { 
                            CourseSetupScreen(
                                viewModel = gameViewModel,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            ) { c -> 
                                gameViewModel.setupGame(
                                    players = gameViewModel.activePlayers.value, 
                                    course = c,
                                    matchInTeamMode = gameViewModel.useTeams.value,
                                    activeTeamsList = gameViewModel.teams.value
                                )
                                navController.navigate("scorecard") 
                            } 
                        }
                        
                        composable("scorecard") {
                            val context = LocalContext.current
                            DisposableEffect(Unit) {
                                val activity = context as? ComponentActivity
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                onDispose { }
                            }
                            ScorecardScreen(
                                viewModel = gameViewModel,
                                onMenuClick = { scope.launch { drawerState.open() } }
                            ) { navController.navigate("leaderboard") }
                        }
                        
                        composable("leaderboard") {
                            val context = LocalContext.current
                            DisposableEffect(Unit) {
                                val activity = context as? ComponentActivity
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                                onDispose { 
                                    activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT 
                                }
                            }
                            LeaderboardScreen(
                                viewModel = gameViewModel,
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onBackToScoring = { navController.popBackStack() },
                                onReset = { 
                                    navController.navigate("home") { popUpTo("home") { inclusive = true } } 
                                }
                            )
                        }
                        
                        composable("history") {
                            MatchHistoryScreen(
                                viewModel = gameViewModel, 
                                onMenuClick = { scope.launch { drawerState.open() } },
                                onBack = { navController.popBackStack() },
                                onResumeRound = {
                                    navController.navigate("scorecard") {
                                        popUpTo("home") 
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DrawerHeaderBar(title: String, isDarkMode: Boolean, onMenuClick: () -> Unit, onThemeToggle: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)) {
                Text("☰", fontSize = 28.sp, color = getTextPrimary(isDarkMode), fontWeight = FontWeight.ExtraBold)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = getTextPrimary(isDarkMode))
        }
        if (onThemeToggle != null) {
            IconButton(onClick = onThemeToggle) {
                Text(if (isDarkMode) "☀️" else "🌙", fontSize = 24.sp)
            }
        }
    }
}

@Composable
fun LeaderboardScreen(viewModel: GameViewModel, onMenuClick: () -> Unit, onBackToScoring: () -> Unit, onReset: () -> Unit) {
    val players by viewModel.activePlayers.collectAsState()
    val scores by viewModel.scores.collectAsState()
    val pressHole by viewModel.pressHole.collectAsState()
    val course by viewModel.selectedCourse.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useTeams by viewModel.useTeams.collectAsState()
    val teams by viewModel.teams.collectAsState()
    
    val calculationOutput = calculateJunkDots(scores, pressHole, course, teams, useTeams)
    val finalCalculatedJunk = calculationOutput.entityTotals
    val breakdownMatrix = calculationOutput.holeBreakdowns
    
    val horizontalScrollState = rememberScrollState()
    val appBg = getAppBg(isDarkMode)
    val cardBg = getCardBg(isDarkMode)
    val textPrimary = getTextPrimary(isDarkMode)
    val goldColor = getGoldThemeColor(isDarkMode)

    Surface(modifier = Modifier.fillMaxSize(), color = appBg) {
        Column(modifier = Modifier.padding(12.dp)) {
            DrawerHeaderBar("Leaderboard", isDarkMode, onMenuClick, null)
            
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = onBackToScoring, 
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDarkMode) Color(0xFF2E2E2E) else Color(0xFFD0D0D0)), 
                    modifier = Modifier.height(50.dp)
                ) {
                    Text("< Back to Scores", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        viewModel.archiveCurrentRound(finalCalculatedJunk)
                        onReset()
                    }, 
                    colors = ButtonDefaults.buttonColors(containerColor = GolfGreen), 
                    modifier = Modifier.height(50.dp)
                ) {
                    Text("Finish Match", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            Column(modifier = Modifier.weight(1f).fillMaxWidth().horizontalScroll(horizontalScrollState)) {
                Row(modifier = Modifier.background(GolfGreen).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (useTeams) " Teams Matrix" else " Player Name", color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(220.dp), fontSize = 20.sp)
                    for (h in 1..9) Text(text = "H$h", color = if (pressHole != null && h >= pressHole!!) Color.Red else Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(84.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                    Text("F9", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                    for (h in 10..18) Text(text = "H$h", color = if (pressHole != null && h >= pressHole!!) Color.Red else Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(84.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                    Text("B9", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                    Text("NET", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                    Text("JUNK", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                }

                LazyColumn(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (useTeams && teams.isNotEmpty()) {
                        items(teams) { team ->
                            val teamBreakdowns = breakdownMatrix[team.id] ?: List(18) { HoleCalculationResult() }
                            val totalDotsEarned = finalCalculatedJunk[team.id] ?: 0
                            
                            var frontNetTotal = 0
                            var backNetTotal = 0
                            for(i in 0..17) {
                                val nets = team.playerIds.mapNotNull { scores[it]?.getOrNull(i) }.filter { it.grossScore > 0 }.map { it.netScore }
                                if(nets.isNotEmpty()) {
                                    if(i < 9) frontNetTotal += nets.minOrNull() ?: 0 else backNetTotal += nets.minOrNull() ?: 0
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth().background(cardBg).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = team.name, color = textPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(220.dp).padding(start = 8.dp), fontSize = 18.sp)
                                for (h in 1..9) {
                                    val memberScores = team.playerIds.mapNotNull { scores[it]?.getOrNull(h - 1) }.filter { it.grossScore > 0 }
                                    val bestBallNet = memberScores.map { it.netScore }.minOrNull()
                                    val label = if (bestBallNet != null) "$bestBallNet" else "-"
                                    val hCalc = teamBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                    val dots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                    Column(modifier = Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(label, color = textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        if (dots > 0) Text("+$dots", color = goldColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text(text = "$frontNetTotal", color = goldColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 24.sp)
                                for (h in 10..18) {
                                    val memberScores = team.playerIds.mapNotNull { scores[it]?.getOrNull(h - 1) }.filter { it.grossScore > 0 }
                                    val bestBallNet = memberScores.map { it.netScore }.minOrNull()
                                    val label = if (bestBallNet != null) "$bestBallNet" else "-"
                                    val hCalc = teamBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                    val dots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                    Column(modifier = Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(label, color = textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        if (dots > 0) Text("+$dots", color = goldColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                    }
                                }
                                Text(text = "$backNetTotal", color = goldColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 24.sp)
                                Text(text = "${frontNetTotal + backNetTotal}", color = textPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 24.sp)
                                Text(text = "$totalDotsEarned", color = goldColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center, fontSize = 26.sp)
                            }
                        }
                    } else {
                        items(players) { player ->
                            val playerHoleScores = scores[player.id] ?: List(18) { HoleScore() }
                            val playerBreakdowns = breakdownMatrix[player.id] ?: List(18) { HoleCalculationResult() }
                            val frontNet = playerHoleScores.take(9).sumOf { it.netScore }
                            val backNet = playerHoleScores.drop(9).take(9).sumOf { it.netScore }
                            val totalDotsEarned = finalCalculatedJunk[player.id] ?: 0

                            Row(modifier = Modifier.fillMaxWidth().background(cardBg).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = player.name, color = textPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(220.dp).padding(start = 8.dp), fontSize = 20.sp)
                                for (h in 1..9) {
                                    val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                    Column(modifier = Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (hScore.grossScore > 0) {
                                            Text("${hScore.grossScore}/${hScore.netScore}", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            val hCalc = playerBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                            val dots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                            if (dots > 0) Text("+$dots", color = goldColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                        } else Text("-", color = Color.Gray, fontSize = 20.sp)
                                    }
                                }
                                Text(text = "$frontNet", color = goldColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 24.sp)
                                for (h in 10..18) {
                                    val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                    Column(modifier = Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        if (hScore.grossScore > 0) {
                                            Text("${hScore.grossScore}/${hScore.netScore}", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                            val hCalc = playerBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                            val dots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                            if (dots > 0) Text("+$dots", color = goldColor, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                                        } else Text("-", color = Color.Gray, fontSize = 20.sp)
                                    }
                                }
                                Text(text = "$backNet", color = goldColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 24.sp)
                                Text(text = "${frontNet + backNet}", color = textPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 24.sp)
                                Text(text = "$totalDotsEarned", color = goldColor, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center, fontSize = 26.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}