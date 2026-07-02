package com.steve.junker

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
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
import com.steve.junker.data.models.Player
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Rect
import com.steve.junker.data.models.Team

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
                                text = "JUNKER", 
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
                    NavHost(navController = navController, startDestination = "home") {
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
    var expandedTeamIds by remember { mutableStateOf(emptySet<String>()) }
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    
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
            
            if (!isLandscape) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onBackToScoring, 
                        colors = ButtonDefaults.buttonColors(containerColor = if (isDarkMode) Color(0xFF2E2E2E) else Color(0xFFD0D0D0)), 
                        modifier = Modifier.height(50.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("< Back", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                             val sdf = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
                             val currentDate = sdf.format(java.util.Date())
                            
                            // Generate high-resolution, full scorecard image programmatically
                            val finalBitmap = drawScorecardToBitmap(context, players, scores, course, teams, useTeams, finalCalculatedJunk)
                            
                            // Save to cache directory
                            val cachePath = java.io.File(context.cacheDir, "images")
                            cachePath.mkdirs()
                            val file = java.io.File(cachePath, "junker_leaderboard.png")
                            try {
                                val stream = java.io.FileOutputStream(file)
                                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                stream.close()
                                
                                val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "com.steve.junker.fileprovider", file)
                                
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Junker Scorecard - ${course?.name ?: "Match"}")
                                    putExtra(android.content.Intent.EXTRA_TEXT, "Attached is the Junker Match Scorecard.\n\nDate: $currentDate\nCourse: ${course?.name ?: "Unknown Course"}\nPar: ${course?.holePars?.sum() ?: 72}")
                                    putExtra(android.content.Intent.EXTRA_STREAM, contentUri)
                                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                try {
                                    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Scorecard Image"))
                                } catch (ex: Exception) {
                                    android.widget.Toast.makeText(context, "No app available to perform this action", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Failed to share image: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)), 
                        modifier = Modifier.height(50.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Email", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.archiveCurrentRound(finalCalculatedJunk)
                            onReset()
                        }, 
                        colors = ButtonDefaults.buttonColors(containerColor = GolfGreen), 
                        modifier = Modifier.height(50.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("Finish", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(modifier = Modifier.background(GolfGreen).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (useTeams) " Teams Matrix" else " Player Name", color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(220.dp), fontSize = 20.sp)
                    Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
                        for (h in 1..9) Text(text = "H$h", color = if (pressHole != null && h >= pressHole!!) Color.Red else Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(84.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                        Text("F9", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                        for (h in 10..18) Text(text = "H$h", color = if (pressHole != null && h >= pressHole!!) Color.Red else Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(84.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                        Text("B9", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                        Text("NET", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                        Text("JUNK", color = GoldAccent, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (useTeams && teams.isNotEmpty()) {
                        teams.forEach { team ->
                            item(key = team.id) {
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

                                val isExpanded = expandedTeamIds.contains(team.id)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cardBg)
                                        .clickable {
                                            expandedTeamIds = if (isExpanded) {
                                                expandedTeamIds - team.id
                                            } else {
                                                expandedTeamIds + team.id
                                            }
                                        }
                                        .padding(vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isExpanded) "▼  ${team.name}" else "▶  ${team.name}", 
                                        color = textPrimary, 
                                        fontWeight = FontWeight.ExtraBold, 
                                        modifier = Modifier.width(220.dp).padding(start = 8.dp), 
                                        fontSize = 18.sp
                                    )
                                    Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
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
                                        val teamFront9Dots = teamBreakdowns.take(9).sumOf { it.basicJunkDots + it.skinDotsWon + it.ctpDotsWon } + (
                                            if (teams.associate { t ->
                                                t.id to (0..8).sumOf { i ->
                                                    t.playerIds.mapNotNull { scores[it]?.getOrNull(i) }.filter { it.grossScore > 0 }.map { it.netScore }.minOrNull() ?: 0
                                                }
                                            }.let { nets ->
                                                val minN = nets.values.minOrNull()
                                                minN != null && nets.filterValues { it == minN }.size == 1 && nets[team.id] == minN
                                            }) 1 else 0
                                        )
                                        Column(modifier = Modifier.width(95.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                            Text(text = "$frontNetTotal", color = textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                            if (teamFront9Dots > 0) {
                                                Text(text = "+$teamFront9Dots", color = goldColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                            }
                                        }
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
                            }
                            if (expandedTeamIds.contains(team.id)) {
                                items(team.playerIds, key = { "${team.id}_$it" }) { playerId ->
                                    val player = players.find { it.id == playerId }
                                    if (player != null) {
                                        val playerHoleScores = scores[player.id] ?: List(18) { HoleScore() }
                                        val frontNet = playerHoleScores.take(9).sumOf { it.netScore }
                                        val backNet = playerHoleScores.drop(9).take(9).sumOf { it.netScore }
                                        
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(cardBg.copy(alpha = 0.6f))
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "    ↳ ${player.name}", 
                                                color = textPrimary.copy(alpha = 0.8f), 
                                                fontWeight = FontWeight.Medium, 
                                                modifier = Modifier.width(220.dp).padding(start = 8.dp), 
                                                fontSize = 16.sp
                                            )
                                            Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
                                                for (h in 1..9) {
                                                    val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                                    Column(modifier = Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                        if (hScore.grossScore > 0) {
                                                            Text("${hScore.grossScore}", color = textPrimary.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                                        } else {
                                                            Text("-", color = Color.Gray, fontSize = 18.sp)
                                                        }
                                                    }
                                                }
                                                Text(text = "$frontNet", color = goldColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                                                for (h in 10..18) {
                                                    val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                                    Column(modifier = Modifier.width(84.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                                        if (hScore.grossScore > 0) {
                                                            Text("${hScore.grossScore}", color = textPrimary.copy(alpha = 0.8f), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                                        } else {
                                                            Text("-", color = Color.Gray, fontSize = 18.sp)
                                                        }
                                                    }
                                                }
                                                Text(text = "$backNet", color = goldColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                                                Text(text = "${frontNet + backNet}", color = textPrimary.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, modifier = Modifier.width(95.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                                                Text(text = "-", color = Color.Gray, fontWeight = FontWeight.Bold, modifier = Modifier.width(110.dp), textAlign = TextAlign.Center, fontSize = 20.sp)
                                            }
                                        }
                                    }
                                }
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
                                Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
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
                                    val playerFront9Dots = playerBreakdowns.take(9).sumOf { it.basicJunkDots + it.skinDotsWon + it.ctpDotsWon } + (
                                        if (players.associate { p ->
                                            val pScores = scores[p.id] ?: List(18) { HoleScore() }
                                            p.id to pScores.take(9).sumOf { it.netScore }
                                        }.let { nets ->
                                            val minN = nets.values.minOrNull()
                                            minN != null && nets.filterValues { it == minN }.size == 1 && nets[player.id] == minN
                                        }) 1 else 0
                                    )
                                    Column(modifier = Modifier.width(95.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                        Text(text = "$frontNet", color = textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                        if (playerFront9Dots > 0) {
                                            Text(text = "+$playerFront9Dots", color = goldColor, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
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
}

fun calculatePlayerDotsForHole(hScore: HoleScore, holeIndex: Int, course: Course?, pressHole: Int?): Int {
    if (hScore.grossScore <= 0 || course == null) return 0
    val holePar = course.holePars.getOrNull(holeIndex) ?: 4
    val multiplier = if (pressHole != null && (holeIndex + 1) >= pressHole) 2 else 1
    
    var dots = 0
    if (hScore.hasPoley) dots += 1
    if (hScore.hasDoublePoley) dots += 2
    
    val scoreRelationToPar = hScore.grossScore - holePar
    if (scoreRelationToPar == -1) dots += 2
    else if (scoreRelationToPar <= -2) dots += 5
    
    val madeParOrBetter = hScore.grossScore <= holePar
    if (madeParOrBetter) {
        if (hScore.hasDrive) dots += 1
        if (hScore.hasSandy) dots += 1
        if (hScore.hasJacques) dots += 1
        if (hScore.hasLostBoy) dots += 1
        if (holePar == 3 && hScore.hasCtp) {
            dots += 1
        }
    }
    return dots * multiplier
}

data class Scorecell(
    val mainText: String,
    val subText: String = ""
)

data class ScorecardRow(
    val type: String,
    val name: String,
    val scores: List<Scorecell>,
    val isTeam: Boolean = false,
    val isPlayerSub: Boolean = false
)

fun drawScorecardToBitmap(
    context: android.content.Context,
    players: List<Player>,
    scores: Map<String, List<HoleScore>>,
    course: Course?,
    teams: List<Team>,
    useTeams: Boolean,
    finalCalculatedJunk: Map<String, Int>
): android.graphics.Bitmap {
    val sdf = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
    val currentDate = sdf.format(java.util.Date())
    
    // Calculate the junk dots for each entity and hole
    val calculationOutput = com.steve.junker.data.models.calculateJunkDots(scores, null, course, teams, useTeams)
    val breakdownMatrix = calculationOutput.holeBreakdowns
    
    // 1. Build the rows data list
    val rows = mutableListOf<ScorecardRow>()
    
    // Par row scores
    val parScores = mutableListOf<Scorecell>()
    for (h in 1..9) parScores.add(Scorecell((course?.holePars?.getOrNull(h - 1) ?: 4).toString()))
    val f9ParSum = course?.holePars?.take(9)?.sum() ?: 36
    parScores.add(Scorecell(f9ParSum.toString()))
    for (h in 10..18) parScores.add(Scorecell((course?.holePars?.getOrNull(h - 1) ?: 4).toString()))
    val b9ParSum = course?.holePars?.drop(9)?.take(9)?.sum() ?: 36
    parScores.add(Scorecell(b9ParSum.toString()))
    parScores.add(Scorecell((f9ParSum + b9ParSum).toString()))
    parScores.add(Scorecell("-"))
    
    rows.add(ScorecardRow("PAR", "Par", parScores))
    
    if (useTeams) {
        teams.forEach { team ->
            val teamBreakdowns = breakdownMatrix[team.id] ?: List(18) { HoleCalculationResult() }
            val playerHoleScoresList = team.playerIds.map { scores[it] ?: List(18) { HoleScore() } }
            val f9Net = (0..8).sumOf { i ->
                playerHoleScoresList.mapNotNull { it.getOrNull(i) }.filter { it.grossScore > 0 }.map { it.netScore }.minOrNull() ?: 0
            }
            val b9Net = (9..17).sumOf { i ->
                playerHoleScoresList.mapNotNull { it.getOrNull(i) }.filter { it.grossScore > 0 }.map { it.netScore }.minOrNull() ?: 0
            }
            val totalNet = f9Net + b9Net
            val dots = finalCalculatedJunk[team.id] ?: 0
            
            val teamFront9Dots = teamBreakdowns.take(9).sumOf { it.basicJunkDots + it.skinDotsWon + it.ctpDotsWon } + (
                if (teams.associate { t ->
                    t.id to (0..8).sumOf { i ->
                        t.playerIds.mapNotNull { scores[it]?.getOrNull(i) }.filter { it.grossScore > 0 }.map { it.netScore }.minOrNull() ?: 0
                    }
                }.let { nets ->
                    val minN = nets.values.minOrNull()
                    minN != null && nets.filterValues { it == minN }.size == 1 && nets[team.id] == minN
                }) 1 else 0
            )
            
            val teamScores = mutableListOf<Scorecell>()
            for (h in 1..9) {
                val memberScores = team.playerIds.mapNotNull { scores[it]?.getOrNull(h - 1) }.filter { it.grossScore > 0 }
                val bestBallNet = memberScores.map { it.netScore }.minOrNull()
                val label = if (bestBallNet != null) "$bestBallNet" else "-"
                
                val hCalc = teamBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                val cellDots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                val subText = if (cellDots > 0) "+$cellDots" else ""
                
                teamScores.add(Scorecell(label, subText))
            }
            // F9 Net & Dots
            teamScores.add(Scorecell(f9Net.toString(), if (teamFront9Dots > 0) "+$teamFront9Dots" else ""))
            
            for (h in 10..18) {
                val memberScores = team.playerIds.mapNotNull { scores[it]?.getOrNull(h - 1) }.filter { it.grossScore > 0 }
                val bestBallNet = memberScores.map { it.netScore }.minOrNull()
                val label = if (bestBallNet != null) "$bestBallNet" else "-"
                
                val hCalc = teamBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                val cellDots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                val subText = if (cellDots > 0) "+$cellDots" else ""
                
                teamScores.add(Scorecell(label, subText))
            }
            // B9 / NET / JUNK
            teamScores.add(Scorecell(b9Net.toString()))
            teamScores.add(Scorecell(totalNet.toString()))
            teamScores.add(Scorecell(if (dots >= 0) "+$dots" else "$dots"))
            
            rows.add(ScorecardRow("TEAM", team.name, teamScores, isTeam = true))
            
            team.playerIds.forEach { pId ->
                val player = players.find { it.id == pId }
                if (player != null) {
                    val pScores = scores[player.id] ?: List(18) { HoleScore() }
                    val pf9 = pScores.take(9).sumOf { it.netScore }
                    val pb9 = pScores.drop(9).take(9).sumOf { it.netScore }
                    val pTotal = pScores.sumOf { it.netScore }
                    
                    val pScoresList = mutableListOf<Scorecell>()
                    for (h in 1..9) {
                        val g = pScores.getOrNull(h - 1)?.grossScore ?: 0
                        pScoresList.add(Scorecell(if (g > 0) "$g" else "-"))
                    }
                    pScoresList.add(Scorecell(pf9.toString()))
                    for (h in 10..18) {
                        val g = pScores.getOrNull(h - 1)?.grossScore ?: 0
                        pScoresList.add(Scorecell(if (g > 0) "$g" else "-"))
                    }
                    pScoresList.add(Scorecell(pb9.toString()))
                    pScoresList.add(Scorecell(pTotal.toString()))
                    pScoresList.add(Scorecell("-"))
                    
                    rows.add(ScorecardRow("PLAYER_SUB", "  ↳ ${player.name}", pScoresList, isPlayerSub = true))
                }
            }
        }
    } else {
        players.forEach { player ->
            val pScores = scores[player.id] ?: List(18) { HoleScore() }
            val playerBreakdowns = breakdownMatrix[player.id] ?: List(18) { HoleCalculationResult() }
            val frontNet = pScores.take(9).sumOf { it.netScore }
            val backNet = pScores.drop(9).take(9).sumOf { it.netScore }
            val totalNet = frontNet + backNet
            val dots = finalCalculatedJunk[player.id] ?: 0
            
            val playerFront9Dots = playerBreakdowns.take(9).sumOf { it.basicJunkDots + it.skinDotsWon + it.ctpDotsWon } + (
                if (players.associate { p ->
                    val ps = scores[p.id] ?: List(18) { HoleScore() }
                    p.id to ps.take(9).sumOf { it.netScore }
                }.let { nets ->
                    val minN = nets.values.minOrNull()
                    minN != null && nets.filterValues { it == minN }.size == 1 && nets[player.id] == minN
                }) 1 else 0
            )
            
            val pScoresList = mutableListOf<Scorecell>()
            for (h in 1..9) {
                val score = pScores.getOrNull(h - 1)
                val g = score?.grossScore ?: 0
                val n = score?.netScore ?: 0
                val label = if (g > 0) "$g/$n" else "-"
                
                val hCalc = playerBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                val cellDots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                val subText = if (cellDots > 0) "+$cellDots" else ""
                
                pScoresList.add(Scorecell(label, subText))
            }
            // F9 Net & Dots
            pScoresList.add(Scorecell(frontNet.toString(), if (playerFront9Dots > 0) "+$playerFront9Dots" else ""))
            
            for (h in 10..18) {
                val score = pScores.getOrNull(h - 1)
                val g = score?.grossScore ?: 0
                val n = score?.netScore ?: 0
                val label = if (g > 0) "$g/$n" else "-"
                
                val hCalc = playerBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                val cellDots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                val subText = if (cellDots > 0) "+$cellDots" else ""
                
                pScoresList.add(Scorecell(label, subText))
            }
            // B9 / NET / JUNK
            pScoresList.add(Scorecell(backNet.toString()))
            pScoresList.add(Scorecell(totalNet.toString()))
            pScoresList.add(Scorecell(if (dots >= 0) "+$dots" else "$dots"))
            
            rows.add(ScorecardRow("PLAYER_SINGLE", player.name, pScoresList))
        }
    }
    
    // 2. Setup paint assets
    val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 36f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    
    val subtitlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.DKGRAY
        textSize = 24f
    }
    
    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 22f
    }
    
    val headerTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    
    val boldTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    
    val goldTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E65100")
        textSize = 22f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    
    val cellGoldTextPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.parseColor("#E65100")
        textSize = 16f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    
    val bgPaint = android.graphics.Paint()
    
    val borderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#CCCCCC")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    // 3. Layout dimensions
    val marginLeft = 40
    val marginTop = 140
    val rowHeight = 66
    val nameColWidth = 260
    val scoreColWidth = 66
    
    val totalColsWidth = nameColWidth + (22 * scoreColWidth)
    val width = totalColsWidth + (marginLeft * 2)
    val height = marginTop + ((rows.size + 1) * rowHeight) + 80
    
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Draw white background
    canvas.drawColor(android.graphics.Color.WHITE)
    
    // Draw Title & Info
    canvas.drawText("JUNKER MATCH SCORECARD", marginLeft.toFloat(), 50f, titlePaint)
    canvas.drawText("Course: ${course?.name ?: "Unknown Course"}  |  Date: $currentDate", marginLeft.toFloat(), 95f, subtitlePaint)
    
    // Draw Header Row
    val headerY = marginTop.toFloat()
    bgPaint.color = android.graphics.Color.parseColor("#1B5E20") // GolfGreen
    canvas.drawRect(marginLeft.toFloat(), headerY, (marginLeft + totalColsWidth).toFloat(), headerY + rowHeight, bgPaint)
    
    // Header cells
    var currentX = marginLeft
    canvas.drawText("Name", (currentX + 15).toFloat(), headerY + 41, headerTextPaint)
    currentX += nameColWidth
    
    // Headers list
    val headers = mutableListOf<String>()
    for (h in 1..9) headers.add("H$h")
    headers.add("F9")
    for (h in 10..18) headers.add("H$h")
    headers.add("B9")
    headers.add("NET")
    headers.add("JUNK")
    
    headers.forEachIndexed { index, header ->
        val xCenter = currentX + (scoreColWidth / 2)
        val textWidth = headerTextPaint.measureText(header)
        canvas.drawText(header, xCenter - (textWidth / 2), headerY + 41, headerTextPaint)
        currentX += scoreColWidth
    }
    
    // Draw Rows
    var currentY = headerY + rowHeight
    rows.forEach { row ->
        // Draw row background depending on type
        when (row.type) {
            "PAR" -> bgPaint.color = android.graphics.Color.parseColor("#F5F5F5")
            "TEAM" -> bgPaint.color = android.graphics.Color.parseColor("#E8F5E9")
            else -> bgPaint.color = android.graphics.Color.WHITE
        }
        canvas.drawRect(marginLeft.toFloat(), currentY, (marginLeft + totalColsWidth).toFloat(), currentY + rowHeight, bgPaint)
        
        // Draw Name cell
        currentX = marginLeft
        val currentPaint = when (row.type) {
            "PAR", "TEAM" -> boldTextPaint
            "PLAYER_SUB" -> textPaint.apply { color = android.graphics.Color.parseColor("#555555") }
            else -> textPaint
        }
        canvas.drawText(row.name, (currentX + 15).toFloat(), currentY + 41, currentPaint)
        currentX += nameColWidth
        
        // Draw Score cells
        row.scores.forEachIndexed { colIndex, cell ->
            val isGoldCol = (colIndex == 21 && cell.mainText != "-" && !cell.mainText.startsWith("0")) // JUNK col dots
            val cellPaint = when {
                isGoldCol -> goldTextPaint
                row.type == "PAR" || row.type == "TEAM" || colIndex == 9 || colIndex == 19 || colIndex == 20 -> boldTextPaint
                row.type == "PLAYER_SUB" -> textPaint.apply { color = android.graphics.Color.parseColor("#555555") }
                else -> textPaint
            }
            
            val xCenter = currentX + (scoreColWidth / 2)
            
            if (cell.subText.isNotEmpty()) {
                // Two lines of text (Score above, dots below)
                val mainWidth = cellPaint.measureText(cell.mainText)
                canvas.drawText(cell.mainText, xCenter - (mainWidth / 2), currentY + 30, cellPaint)
                
                val subWidth = cellGoldTextPaint.measureText(cell.subText)
                canvas.drawText(cell.subText, xCenter - (subWidth / 2), currentY + 54, cellGoldTextPaint)
            } else {
                // Centered single line of text
                val textWidth = cellPaint.measureText(cell.mainText)
                canvas.drawText(cell.mainText, xCenter - (textWidth / 2), currentY + 41, cellPaint)
            }
            
            currentX += scoreColWidth
        }
        
        currentY += rowHeight
    }
    
    // Draw Grid borders
    var drawY = headerY
    for (r in 0..(rows.size + 1)) {
        canvas.drawLine(marginLeft.toFloat(), drawY, (marginLeft + totalColsWidth).toFloat(), drawY, borderPaint)
        drawY += rowHeight
    }
    
    var drawX = marginLeft.toFloat()
    canvas.drawLine(drawX, headerY, drawX, headerY + ((rows.size + 1) * rowHeight), borderPaint)
    drawX += nameColWidth
    for (c in 1..22) {
        canvas.drawLine(drawX, headerY, drawX, headerY + ((rows.size + 1) * rowHeight), borderPaint)
        drawX += scoreColWidth
    }
    canvas.drawLine(drawX, headerY, drawX, headerY + ((rows.size + 1) * rowHeight), borderPaint)
    
    return bitmap
}