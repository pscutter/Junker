package com.steve.junker

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import com.steve.junker.data.repository.AirtableJunkerRepository
import com.steve.junker.data.repository.RoomJunkerRepository
import com.steve.junker.ui.screens.*
import com.steve.junker.ui.viewmodel.GameViewModel
import com.steve.junker.data.models.GameType
import com.steve.junker.data.models.HoleScore
import com.steve.junker.data.models.Course
import com.steve.junker.data.models.HoleCalculationResult
import com.steve.junker.data.models.calculateJunkDots
import com.steve.junker.data.models.calculateNinesPoints
import com.steve.junker.data.models.Player
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.geometry.Rect
import com.steve.junker.data.models.Team
import androidx.compose.foundation.Canvas

class MainActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // To use local SQLite Room database, swap with RoomJunkerRepository:
        // val database = JunkerDatabase.getDatabase(this)
        // val repository = RoomJunkerRepository(database.playerDao(), database.courseDao(), database.gameRoundDao())
        
        // Using Airtable database:
        val repository = AirtableJunkerRepository(applicationContext)
        gameViewModel.initRepository(repository)

        val deviceId = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
        gameViewModel.setCurrentDeviceId(deviceId)

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
                                        gameViewModel.refreshPlayers()
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
                                val selectedGameType = gameViewModel.gameType.value
                                val effectiveUseTeams = selectedGameType == com.steve.junker.data.models.GameType.RAW_JUNKER && gameViewModel.useTeams.value
                                gameViewModel.setupGame(
                                    players = gameViewModel.activePlayers.value, 
                                    course = c,
                                    matchInTeamMode = effectiveUseTeams,
                                    activeTeamsList = if (effectiveUseTeams) gameViewModel.teams.value else emptyList(),
                                    gameType = selectedGameType,
                                    isBlitzEnabled = if (selectedGameType == com.steve.junker.data.models.GameType.NINES) gameViewModel.isBlitzEnabled.value else false
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
    val pressHoles by viewModel.pressHoles.collectAsState()
    val course by viewModel.selectedCourse.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useTeams by viewModel.useTeams.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val gameType by viewModel.gameType.collectAsState()
    val isBlitzEnabled by viewModel.isBlitzEnabled.collectAsState()
    var expandedTeamIds by remember { mutableStateOf(emptySet<String>()) }
    var selectedHolePlayerForPopup by remember { mutableStateOf<Pair<Player, Int>?>(null) }
    val isRoundLocked by viewModel.isRoundLocked.collectAsState()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val context = LocalContext.current
    
    val calculationOutput = calculateJunkDots(scores, pressHoles, course, teams, useTeams)
    val ninesOutput = if (gameType == GameType.NINES) calculateNinesPoints(scores, players, isBlitzEnabled) else null
    val finalCalculatedJunk = if (gameType == GameType.NINES) ninesOutput!!.playerTotals else calculationOutput.entityTotals
    val breakdownMatrix = calculationOutput.holeBreakdowns
    
    val horizontalScrollState = rememberScrollState()
    val appBg = getAppBg(isDarkMode)
    val cardBg = getCardBg(isDarkMode)
    val textPrimary = getTextPrimary(isDarkMode)
    val textSecondary = getTextSecondary(isDarkMode)
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
                            val finalBitmap = drawScorecardToBitmap(context, players, scores, course, teams, useTeams, finalCalculatedJunk, pressHoles)
                            
                            // Save to cache directory
                            val cachePath = java.io.File(context.cacheDir, "images")
                            cachePath.mkdirs()
                            val file = java.io.File(cachePath, "junker_leaderboard.png")
                            try {
                                val stream = java.io.FileOutputStream(file)
                                finalBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
                                stream.close()
                                
                                val contentUri = androidx.core.content.FileProvider.getUriForFile(context, "com.steve.junker.fileprovider", file)
                                
                                val emailBodyBuilder = StringBuilder()
                                emailBodyBuilder.append("Attached is the Junker Match Scorecard.\n\n")
                                emailBodyBuilder.append("Date: $currentDate\n")
                                emailBodyBuilder.append("Course: ${course?.name ?: "Unknown Course"}\n")
                                emailBodyBuilder.append("Par: ${course?.holePars?.sum() ?: 72}\n\n")

                                emailBodyBuilder.append("--- LEADERBOARD ---\n")
                                if (gameType == GameType.RAW_JUNKER && useTeams && teams.isNotEmpty()) {
                                    teams.forEach { team ->
                                        val totalDots = finalCalculatedJunk[team.id] ?: 0
                                        emailBodyBuilder.append("${team.name}: $totalDots dots\n")
                                    }
                                } else {
                                    val unitLabel = if (gameType == GameType.NINES) "pts" else "dots"
                                    players.forEach { player ->
                                        val totalDots = finalCalculatedJunk[player.id] ?: 0
                                        emailBodyBuilder.append("${player.name}: $totalDots $unitLabel\n")
                                    }
                                }
                                emailBodyBuilder.append("\n")
                                emailBodyBuilder.append("--- HOLE-BY-HOLE DOTS BREAKDOWN ---\n")
                                 for (h in 1..18) {
                                     val par = course?.holePars?.getOrNull(h - 1) ?: 4
                                     val hcp = course?.holeHandicaps?.getOrNull(h - 1) ?: 18
                                     val multiplier = if (pressHoles.isEmpty()) 1 else {
                                         var m = 1
                                         pressHoles.sorted().forEach { pHole ->
                                             if (h >= pHole) m *= 2
                                         }
                                         m
                                     }
                                     val pressStr = if (multiplier > 1) " [PRESS ACTIVE x$multiplier]" else ""
                                     
                                     val holeDetailsBuilder = StringBuilder()
                                    
                                    if (useTeams && teams.isNotEmpty()) {
                                        // 1. Show team skins if any
                                        teams.forEach { team ->
                                            val hCalc = breakdownMatrix[team.id]?.getOrNull(h - 1) ?: HoleCalculationResult()
                                            if (hCalc.skinDotsWon > 0) {
                                                holeDetailsBuilder.append("  - ${team.name}: Skins: ${hCalc.skinDotsWon} dots\n")
                                            }
                                        }
                                        // 2. Show individual player junk details
                                        players.forEach { player ->
                                            val playerHoleScore = scores[player.id]?.getOrNull(h - 1) ?: HoleScore()
                                            val ownerId = teams.find { it.playerIds.contains(player.id) }?.id ?: player.id
                                            val hCalc = breakdownMatrix[ownerId]?.getOrNull(h - 1) ?: HoleCalculationResult()
                                            
                                            val dotsList = mutableListOf<String>()
                                            var playerTotalDots = 0
                                            
                                            if (par == 3 && playerHoleScore.hasCtp && hCalc.ctpDotsWon > 0) {
                                                dotsList.add("CTP: ${hCalc.ctpDotsWon}")
                                                playerTotalDots += hCalc.ctpDotsWon
                                            }
                                            
                                            val scoreRelationToPar = playerHoleScore.grossScore - par
                                            if (playerHoleScore.grossScore > 0) {
                                                if (scoreRelationToPar == -1) {
                                                    dotsList.add("Birdie: ${2 * multiplier}")
                                                    playerTotalDots += 2 * multiplier
                                                } else if (scoreRelationToPar <= -2) {
                                                    dotsList.add("Eagle or Better: ${5 * multiplier}")
                                                    playerTotalDots += 5 * multiplier
                                                }
                                                
                                                val madeParOrBetter = playerHoleScore.grossScore <= par
                                                if (madeParOrBetter) {
                                                    if (playerHoleScore.hasDrive) {
                                                        dotsList.add("Drive: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                    if (playerHoleScore.hasSandy) {
                                                        dotsList.add("Sandy: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                    if (playerHoleScore.hasJacques) {
                                                        dotsList.add("Jacques: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                    if (playerHoleScore.hasLostBoy) {
                                                        dotsList.add("Lost Boy: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                }
                                            }
                                            
                                            if (playerHoleScore.hasPoley) {
                                                dotsList.add("Poley: ${1 * multiplier}")
                                                playerTotalDots += 1 * multiplier
                                            }
                                            if (playerHoleScore.hasDoublePoley) {
                                                dotsList.add("Double Poley: ${2 * multiplier}")
                                                playerTotalDots += 2 * multiplier
                                            }
                                            
                                            if (playerTotalDots > 0) {
                                                holeDetailsBuilder.append("  - ${player.name}: $playerTotalDots dots (${dotsList.joinToString(", ")})\n")
                                            }
                                        }
                                    } else {
                                        // Individual mode: show all dots (skins, CTP, junk) by player
                                        players.forEach { player ->
                                            val hCalc = breakdownMatrix[player.id]?.getOrNull(h - 1) ?: HoleCalculationResult()
                                            val playerHoleScore = scores[player.id]?.getOrNull(h - 1) ?: HoleScore()
                                            
                                            val dotsList = mutableListOf<String>()
                                            var playerTotalDots = 0
                                            
                                            if (hCalc.skinDotsWon > 0) {
                                                dotsList.add("Skins: ${hCalc.skinDotsWon}")
                                                playerTotalDots += hCalc.skinDotsWon
                                            }
                                            if (par == 3 && playerHoleScore.hasCtp && hCalc.ctpDotsWon > 0) {
                                                dotsList.add("CTP: ${hCalc.ctpDotsWon}")
                                                playerTotalDots += hCalc.ctpDotsWon
                                            }
                                            
                                            val scoreRelationToPar = playerHoleScore.grossScore - par
                                            if (playerHoleScore.grossScore > 0) {
                                                if (scoreRelationToPar == -1) {
                                                    dotsList.add("Birdie: ${2 * multiplier}")
                                                    playerTotalDots += 2 * multiplier
                                                } else if (scoreRelationToPar <= -2) {
                                                    dotsList.add("Eagle or Better: ${5 * multiplier}")
                                                    playerTotalDots += 5 * multiplier
                                                }
                                                
                                                val madeParOrBetter = playerHoleScore.grossScore <= par
                                                if (madeParOrBetter) {
                                                    if (playerHoleScore.hasDrive) {
                                                        dotsList.add("Drive: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                    if (playerHoleScore.hasSandy) {
                                                        dotsList.add("Sandy: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                    if (playerHoleScore.hasJacques) {
                                                        dotsList.add("Jacques: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                    if (playerHoleScore.hasLostBoy) {
                                                        dotsList.add("Lost Boy: ${1 * multiplier}")
                                                        playerTotalDots += 1 * multiplier
                                                    }
                                                }
                                            }
                                            
                                            if (playerHoleScore.hasPoley) {
                                                dotsList.add("Poley: ${1 * multiplier}")
                                                playerTotalDots += 1 * multiplier
                                            }
                                            if (playerHoleScore.hasDoublePoley) {
                                                dotsList.add("Double Poley: ${2 * multiplier}")
                                                playerTotalDots += 2 * multiplier
                                            }
                                            
                                            if (playerTotalDots > 0) {
                                                holeDetailsBuilder.append("  - ${player.name}: $playerTotalDots dots (${dotsList.joinToString(", ")})\n")
                                            }
                                        }
                                    }
                                    
                                    if (holeDetailsBuilder.isNotEmpty()) {
                                        emailBodyBuilder.append("\nHole $h (Par $par, HCP $hcp)$pressStr:\n")
                                        emailBodyBuilder.append(holeDetailsBuilder.toString())
                                    }
                                }
                                val emailBodyText = emailBodyBuilder.toString()

                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "image/png"
                                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Junker Scorecard - ${course?.name ?: "Match"}")
                                    putExtra(android.content.Intent.EXTRA_TEXT, emailBodyText)
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
                            if (!isRoundLocked) {
                                viewModel.archiveCurrentRound(finalCalculatedJunk, isLocked = true)
                            }
                            onReset()
                        }, 
                        enabled = !isRoundLocked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = GolfGreen,
                            disabledContainerColor = GolfGreen.copy(alpha = 0.5f)
                        ), 
                        modifier = Modifier.height(50.dp).weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text(if (isRoundLocked) "Close" else "Finish", color = if (isRoundLocked) Color.White.copy(alpha = 0.5f) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Row(modifier = Modifier.background(GolfGreen), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (gameType == GameType.RAW_JUNKER && useTeams) " Teams Matrix" else " Player Name", color = Color.White, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(220.dp).padding(start = 8.dp), fontSize = 20.sp)
                    Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
                        for (h in 1..9) {
                                     Box(modifier = Modifier.width(84.dp).height(56.dp).border(1.dp, Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                         Text(text = "H$h", color = if (gameType == GameType.RAW_JUNKER && pressHoles.isNotEmpty() && h >= pressHoles.min()) Color.Red else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                     }
                                 }
                        Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            Text("F9", color = GoldAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        for (h in 10..18) {
                                     Box(modifier = Modifier.width(84.dp).height(56.dp).border(1.dp, Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                                         Text(text = "H$h", color = if (gameType == GameType.RAW_JUNKER && pressHoles.isNotEmpty() && h >= pressHoles.min()) Color.Red else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                                     }
                                 }
                        Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            Text("B9", color = GoldAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            Text("NET", color = GoldAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                        Box(modifier = Modifier.width(110.dp).height(56.dp).border(1.dp, Color.White.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                            Text(if (gameType == GameType.NINES) "PTS" else "JUNK", color = GoldAccent, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (gameType == GameType.RAW_JUNKER && useTeams && teams.isNotEmpty()) {
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
                                    val lineColor = if (isDarkMode) Color.DarkGray else Color.LightGray
                                    Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
                                         for (h in 1..9) {
                                             val memberScores = team.playerIds.mapNotNull { scores[it]?.getOrNull(h - 1) }.filter { it.grossScore > 0 }
                                             val bestBallNet = memberScores.map { it.netScore }.minOrNull()
                                             val hCalc = teamBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                             val dots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                             Box(modifier = Modifier.width(84.dp).height(56.dp).border(1.dp, lineColor)) {
                                                 if (bestBallNet != null) {
                                                     Canvas(modifier = Modifier.fillMaxSize()) {
                                                         drawLine(
                                                             color = lineColor,
                                                             start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
                                                             end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                                                             strokeWidth = 2f
                                                         )
                                                     }
                                                     Text(
                                                         text = "$bestBallNet",
                                                         color = textPrimary,
                                                         fontSize = 15.sp,
                                                         fontWeight = FontWeight.Bold,
                                                         modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
                                                     )
                                                     if (dots > 0) {
                                                         Text(
                                                             text = "+$dots",
                                                             color = goldColor,
                                                             fontSize = 13.sp,
                                                             fontWeight = FontWeight.ExtraBold,
                                                             modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp)
                                                         )
                                                     }
                                                 } else {
                                                     Text("-", color = Color.Gray, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                                                 }
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
                                         Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor)) {
                                             Canvas(modifier = Modifier.fillMaxSize()) {
                                                 drawLine(
                                                     color = lineColor,
                                                     start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
                                                     end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                                                     strokeWidth = 2f
                                                 )
                                             }
                                             Text(
                                                 text = "$frontNetTotal",
                                                 color = textPrimary,
                                                 fontSize = 16.sp,
                                                 fontWeight = FontWeight.Bold,
                                                 modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp)
                                             )
                                             if (teamFront9Dots > 0) {
                                                 Text(
                                                     text = "+$teamFront9Dots",
                                                     color = goldColor,
                                                     fontSize = 13.sp,
                                                     fontWeight = FontWeight.ExtraBold,
                                                     modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 4.dp)
                                                 )
                                             }
                                         }
                                         for (h in 10..18) {
                                             val memberScores = team.playerIds.mapNotNull { scores[it]?.getOrNull(h - 1) }.filter { it.grossScore > 0 }
                                             val bestBallNet = memberScores.map { it.netScore }.minOrNull()
                                             val hCalc = teamBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                             val dots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                             Box(modifier = Modifier.width(84.dp).height(56.dp).border(1.dp, lineColor)) {
                                                 if (bestBallNet != null) {
                                                     Canvas(modifier = Modifier.fillMaxSize()) {
                                                         drawLine(
                                                             color = lineColor,
                                                             start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
                                                             end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                                                             strokeWidth = 2f
                                                         )
                                                     }
                                                     Text(
                                                         text = "$bestBallNet",
                                                         color = textPrimary,
                                                         fontSize = 15.sp,
                                                         fontWeight = FontWeight.Bold,
                                                         modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 4.dp)
                                                     )
                                                     if (dots > 0) {
                                                         Text(
                                                             text = "+$dots",
                                                             color = goldColor,
                                                             fontSize = 13.sp,
                                                             fontWeight = FontWeight.ExtraBold,
                                                             modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp)
                                                         )
                                                     }
                                                 } else {
                                                     Text("-", color = Color.Gray, fontSize = 20.sp, modifier = Modifier.align(Alignment.Center))
                                                 }
                                             }
                                         }
                                         Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                             Text(text = "$backNetTotal", color = goldColor, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                                         }
                                         Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                             Text(text = "${frontNetTotal + backNetTotal}", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                                         }
                                         Box(modifier = Modifier.width(110.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                             Text(text = "$totalDotsEarned", color = goldColor, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                                         }
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
                                            val lineColor = if (isDarkMode) Color.DarkGray else Color.LightGray
                                            Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
                                                for (h in 1..9) {
                                                    val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                                    val par = course?.holePars?.getOrNull(h - 1) ?: 4
                                                    val wonSkin = isPlayerHoleWinnerForTeam(player, h - 1, scores, breakdownMatrix, teams)
                                                    val hasDots = playerEarnedDotsOnHole(hScore, par) || wonSkin
                                                    Box(
                                                        modifier = Modifier
                                                            .width(84.dp)
                                                            .height(56.dp)
                                                            .border(1.dp, lineColor)
                                                            .clickable {
                                                                if (hScore.grossScore > 0) {
                                                                    selectedHolePlayerForPopup = Pair(player, h)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        GolfScoreBadge(
                                                            score = hScore.grossScore,
                                                            par = par,
                                                            textColor = textPrimary.copy(alpha = 0.8f),
                                                            hasDots = hasDots
                                                        )
                                                    }
                                                }
                                                Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                                    Text(text = "$frontNet", color = goldColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                }
                                                for (h in 10..18) {
                                                    val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                                    val par = course?.holePars?.getOrNull(h - 1) ?: 4
                                                    val wonSkin = isPlayerHoleWinnerForTeam(player, h - 1, scores, breakdownMatrix, teams)
                                                    val hasDots = playerEarnedDotsOnHole(hScore, par) || wonSkin
                                                    Box(
                                                        modifier = Modifier
                                                            .width(84.dp)
                                                            .height(56.dp)
                                                            .border(1.dp, lineColor)
                                                            .clickable {
                                                                if (hScore.grossScore > 0) {
                                                                    selectedHolePlayerForPopup = Pair(player, h)
                                                                }
                                                            },
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        GolfScoreBadge(
                                                            score = hScore.grossScore,
                                                            par = par,
                                                            textColor = textPrimary.copy(alpha = 0.8f),
                                                            hasDots = hasDots
                                                        )
                                                    }
                                                }
                                                Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                                    Text(text = "$backNet", color = goldColor.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                }
                                                Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                                    Text(text = "${frontNet + backNet}", color = textPrimary.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                }
                                                Box(modifier = Modifier.width(110.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                                    Text(text = "-", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        val displayPlayers = if (gameType == GameType.NINES) {
                            players.sortedByDescending { finalCalculatedJunk[it.id] ?: 0 }
                        } else {
                            players
                        }
                        items(displayPlayers) { player ->
                            val playerHoleScores = scores[player.id] ?: List(18) { HoleScore() }
                            val playerBreakdowns = breakdownMatrix[player.id] ?: List(18) { HoleCalculationResult() }
                            val frontNet = playerHoleScores.take(9).sumOf { it.netScore }
                            val backNet = playerHoleScores.drop(9).take(9).sumOf { it.netScore }
                            val totalDotsEarned = finalCalculatedJunk[player.id] ?: 0

                            Row(modifier = Modifier.fillMaxWidth().background(cardBg).padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = player.name, color = textPrimary, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(220.dp).padding(start = 8.dp), fontSize = 20.sp)
                                Row(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState), verticalAlignment = Alignment.CenterVertically) {
                                     val lineColor = if (isDarkMode) Color.DarkGray else Color.LightGray
                                     for (h in 1..9) {
                                         val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                         Box(
                                             modifier = Modifier
                                                 .width(84.dp)
                                                 .height(56.dp)
                                                 .border(1.dp, lineColor)
                                                 .clickable {
                                                     if (hScore.grossScore > 0) {
                                                         selectedHolePlayerForPopup = Pair(player, h)
                                                     }
                                                 }
                                         ) {
                                             if (hScore.grossScore > 0) {
                                                 val hCalc = playerBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                                 val dots = if (gameType == GameType.NINES) {
                                                     ninesOutput?.holePoints?.get(player.id)?.getOrNull(h - 1) ?: 0
                                                 } else {
                                                     hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                                 }
                                                 Canvas(modifier = Modifier.fillMaxSize()) {
                                                     drawLine(
                                                         color = lineColor,
                                                         start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
                                                         end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                                                         strokeWidth = 2f
                                                     )
                                                 }
                                                 val par = course?.holePars?.getOrNull(h - 1) ?: 4
                                                 val hasDots = if (gameType == GameType.NINES) {
                                                     dots > 0
                                                 } else {
                                                     playerEarnedDotsOnHole(hScore, par) || hCalc.skinDotsWon > 0
                                                 }
                                                 GolfScoreBadge(
                                                     score = hScore.grossScore,
                                                     par = par,
                                                     textColor = textPrimary,
                                                     hasDots = hasDots,
                                                     modifier = Modifier
                                                         .align(Alignment.TopStart)
                                                         .padding(start = 6.dp, top = 4.dp)
                                                 )
                                                 Text(
                                                     text = "${hScore.netScore}",
                                                     color = textPrimary,
                                                     fontSize = 15.sp,
                                                     fontWeight = FontWeight.Bold,
                                                     modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                                                 )
                                                 if (dots > 0) {
                                                     Text(
                                                         text = "+$dots",
                                                         color = goldColor,
                                                         fontSize = 13.sp,
                                                         fontWeight = FontWeight.ExtraBold,
                                                         modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp)
                                                     )
                                                 }
                                             } else {
                                                 Text("-", color = Color.Gray, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
                                             }
                                         }
                                     }
                                     val playerFront9Dots = if (gameType == GameType.NINES) {
                                         ninesOutput?.holePoints?.get(player.id)?.take(9)?.sum() ?: 0
                                     } else {
                                         playerBreakdowns.take(9).sumOf { it.basicJunkDots + it.skinDotsWon + it.ctpDotsWon } + (
                                             if (players.associate { p ->
                                                 val pScores = scores[p.id] ?: List(18) { HoleScore() }
                                                 p.id to pScores.take(9).sumOf { it.netScore }
                                             }.let { nets ->
                                                 val minN = nets.values.minOrNull()
                                                 minN != null && nets.filterValues { it == minN }.size == 1 && nets[player.id] == minN
                                             }) 1 else 0
                                         )
                                     }
                                     Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor)) {
                                         Canvas(modifier = Modifier.fillMaxSize()) {
                                             drawLine(
                                                 color = lineColor,
                                                 start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
                                                 end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                                                 strokeWidth = 2f
                                             )
                                         }
                                         Text(
                                             text = "$frontNet",
                                             color = textPrimary,
                                             fontSize = 16.sp,
                                             fontWeight = FontWeight.Bold,
                                             modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp)
                                         )
                                         if (playerFront9Dots > 0) {
                                             Text(
                                                 text = "+$playerFront9Dots",
                                                 color = goldColor,
                                                 fontSize = 13.sp,
                                                 fontWeight = FontWeight.ExtraBold,
                                                 modifier = Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 4.dp)
                                             )
                                         }
                                     }
                                     for (h in 10..18) {
                                         val hScore = playerHoleScores.getOrNull(h - 1) ?: HoleScore()
                                         Box(
                                             modifier = Modifier
                                                 .width(84.dp)
                                                 .height(56.dp)
                                                 .border(1.dp, lineColor)
                                                 .clickable {
                                                     if (hScore.grossScore > 0) {
                                                         selectedHolePlayerForPopup = Pair(player, h)
                                                     }
                                                 }
                                         ) {
                                             if (hScore.grossScore > 0) {
                                                 val hCalc = playerBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                                                 val dots = if (gameType == GameType.NINES) {
                                                     ninesOutput?.holePoints?.get(player.id)?.getOrNull(h - 1) ?: 0
                                                 } else {
                                                     hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                                                 }
                                                 Canvas(modifier = Modifier.fillMaxSize()) {
                                                     drawLine(
                                                         color = lineColor,
                                                         start = androidx.compose.ui.geometry.Offset(0f, this.size.height),
                                                         end = androidx.compose.ui.geometry.Offset(this.size.width, 0f),
                                                         strokeWidth = 2f
                                                     )
                                                 }
                                                 val par = course?.holePars?.getOrNull(h - 1) ?: 4
                                                 val hasDots = if (gameType == GameType.NINES) {
                                                     dots > 0
                                                 } else {
                                                     playerEarnedDotsOnHole(hScore, par) || hCalc.skinDotsWon > 0
                                                 }
                                                 GolfScoreBadge(
                                                     score = hScore.grossScore,
                                                     par = par,
                                                     textColor = textPrimary,
                                                     hasDots = hasDots,
                                                     modifier = Modifier
                                                         .align(Alignment.TopStart)
                                                         .padding(start = 6.dp, top = 4.dp)
                                                 )
                                                 Text(
                                                     text = "${hScore.netScore}",
                                                     color = textPrimary,
                                                     fontSize = 15.sp,
                                                     fontWeight = FontWeight.Bold,
                                                     modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp)
                                                 )
                                                 if (dots > 0) {
                                                     Text(
                                                         text = "+$dots",
                                                         color = goldColor,
                                                         fontSize = 13.sp,
                                                         fontWeight = FontWeight.ExtraBold,
                                                         modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp)
                                                     )
                                                 }
                                             } else {
                                                 Text("-", color = Color.Gray, fontSize = 18.sp, modifier = Modifier.align(Alignment.Center))
                                             }
                                         }
                                     }
                                     Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                         Text(text = "$backNet", color = goldColor, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                                     }
                                     Box(modifier = Modifier.width(95.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                         Text(text = "${frontNet + backNet}", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
                                     }
                                     Box(modifier = Modifier.width(110.dp).height(56.dp).border(1.dp, lineColor), contentAlignment = Alignment.Center) {
                                         if (gameType == GameType.NINES) {
                                             val maxNinesPts = if (players.size == 3) 162 else 288
                                             Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                 Text(text = "$totalDotsEarned", color = goldColor, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                                 Text(text = "/$maxNinesPts", color = textSecondary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                             }
                                         } else {
                                             Text(text = "$totalDotsEarned", color = goldColor, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp)
                                         }
                                     }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedHolePlayerForPopup?.let { (player, hole) ->
        val par = course?.holePars?.getOrNull(hole - 1) ?: 4
        val hScore = scores[player.id]?.getOrNull(hole - 1) ?: HoleScore()
        val dotsBreakdown = getPlayerDotsBreakdown(
            player = player,
            h = hole,
            scores = scores,
            breakdownMatrix = breakdownMatrix,
            course = course,
            teams = teams,
            useTeams = useTeams,
            pressHoles = pressHoles
        )
        
        AlertDialog(
            onDismissRequest = { selectedHolePlayerForPopup = null },
            title = {
                Text(
                    text = "${player.name} — Hole $hole Details",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Gross Score: ${hScore.grossScore} (Par $par)  |  Net: ${hScore.netScore}",
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                        fontSize = 15.sp
                    )
                    Divider(color = textSecondary.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(4.dp))
                    if (gameType == GameType.NINES) {
                        val ninesHolePts = ninesOutput?.holePoints?.get(player.id)?.getOrNull(hole - 1) ?: 0
                        Text(
                            text = "Nines Points Earned:",
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "• +$ninesHolePts pts",
                            color = if (ninesHolePts > 0) goldColor else textSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    } else {
                        Text(
                            text = "Dots Earned:",
                            fontWeight = FontWeight.ExtraBold,
                            color = textPrimary,
                            fontSize = 16.sp
                        )
                        if (dotsBreakdown.isEmpty()) {
                            Text(
                                text = "• No dots earned on this hole.",
                                color = textSecondary,
                                fontSize = 14.sp
                            )
                        } else {
                            dotsBreakdown.forEach { dot ->
                                Text(
                                    text = "• $dot",
                                    color = goldColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedHolePlayerForPopup = null },
                    colors = ButtonDefaults.buttonColors(containerColor = GolfGreen)
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = cardBg
        )
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
    val subText: String = "",
    val isSplit: Boolean = false
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
    finalCalculatedJunk: Map<String, Int>,
    pressHoles: List<Int>
): android.graphics.Bitmap {
    val sdf = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
    val currentDate = sdf.format(java.util.Date())
    
    // Calculate the junk dots for each entity and hole
    val calculationOutput = com.steve.junker.data.models.calculateJunkDots(scores, pressHoles, course, teams, useTeams)
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
                
                teamScores.add(Scorecell(label, subText, isSplit = (label != "-")))
            }
            // F9 Net & Dots
            teamScores.add(Scorecell(f9Net.toString(), if (teamFront9Dots > 0) "+$teamFront9Dots" else "", isSplit = true))
            
            for (h in 10..18) {
                val memberScores = team.playerIds.mapNotNull { scores[it]?.getOrNull(h - 1) }.filter { it.grossScore > 0 }
                val bestBallNet = memberScores.map { it.netScore }.minOrNull()
                val label = if (bestBallNet != null) "$bestBallNet" else "-"
                
                val hCalc = teamBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                val cellDots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                val subText = if (cellDots > 0) "+$cellDots" else ""
                
                teamScores.add(Scorecell(label, subText, isSplit = (label != "-")))
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
                
                pScoresList.add(Scorecell(label, subText, isSplit = (label != "-")))
            }
            // F9 Net & Dots
            pScoresList.add(Scorecell(frontNet.toString(), if (playerFront9Dots > 0) "+$playerFront9Dots" else "", isSplit = true))
            
            for (h in 10..18) {
                val score = pScores.getOrNull(h - 1)
                val g = score?.grossScore ?: 0
                val n = score?.netScore ?: 0
                val label = if (g > 0) "$g/$n" else "-"
                
                val hCalc = playerBreakdowns.getOrNull(h - 1) ?: HoleCalculationResult()
                val cellDots = hCalc.basicJunkDots + hCalc.skinDotsWon + hCalc.ctpDotsWon
                val subText = if (cellDots > 0) "+$cellDots" else ""
                
                pScoresList.add(Scorecell(label, subText, isSplit = (label != "-")))
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
            
            val isHoleCol = colIndex in 0..8 || colIndex in 10..18
            val isPlayerRow = row.type == "PLAYER_SINGLE" || row.type == "PLAYER_SUB"
            
            if (isHoleCol && isPlayerRow && cell.mainText != "-") {
                val hIndex = if (colIndex in 0..8) colIndex else colIndex - 1
                val par = course?.holePars?.getOrNull(hIndex) ?: 4
                val parts = cell.mainText.split("/")
                val g = parts[0].toIntOrNull() ?: 0
                val n = parts.getOrNull(1)?.toIntOrNull() ?: g
                val relation = g - par
                
                // Find matching player & calculate hasDots
                val player = if (row.type == "PLAYER_SINGLE") {
                    players.find { it.name == row.name }
                } else {
                    players.find { row.name.endsWith(it.name) }
                }
                val playerHoleScores = scores[player?.id] ?: List(18) { HoleScore() }
                val hScore = playerHoleScores.getOrNull(hIndex) ?: HoleScore()
                val wonSkin = if (row.type == "PLAYER_SINGLE") {
                    val hCalc = breakdownMatrix[player?.id]?.getOrNull(hIndex) ?: HoleCalculationResult()
                    hCalc.skinDotsWon > 0
                } else {
                    isPlayerHoleWinnerForTeam(player, hIndex, scores, breakdownMatrix, teams)
                }
                val hasDots = playerEarnedDotsOnHole(hScore, par) || wonSkin
                
                if (row.type == "PLAYER_SINGLE") {
                    // Draw diagonal line
                    canvas.drawLine(
                        currentX.toFloat(),
                        (currentY + rowHeight).toFloat(),
                        (currentX + scoreColWidth).toFloat(),
                        currentY.toFloat(),
                        borderPaint
                    )
                    
                    // Draw Gross Score 'g' in top-left
                    val xG = (currentX + 18).toFloat()
                    val yG = (currentY + 20).toFloat()
                    drawBadgeOnCanvas(canvas, xG, yG, 13f, relation, cellPaint)
                    
                    val gPaint = android.graphics.Paint(cellPaint).apply { textSize = 15f }
                    val gStr = g.toString()
                    val gWidth = gPaint.measureText(gStr)
                    canvas.drawText(gStr, xG - (gWidth / 2), yG + 5, gPaint)
                    
                    // Draw a small dot if dots won
                    if (hasDots) {
                        val dotPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#E65100")
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }
                        canvas.drawCircle(xG + 19f, yG - 14f, 3f, dotPaint)
                    }
                    
                    // Draw Net Score 'n' in bottom-middle
                    val xN = (currentX + 38).toFloat()
                    val yN = (currentY + rowHeight - 16).toFloat()
                    val nPaint = android.graphics.Paint(cellPaint).apply { textSize = 15f }
                    val nStr = n.toString()
                    val nWidth = nPaint.measureText(nStr)
                    canvas.drawText(nStr, xN - (nWidth / 2), yN, nPaint)
                    
                    // Draw dots subtext if any
                    if (cell.subText.isNotEmpty()) {
                        cellGoldTextPaint.textSize = 12f
                        val subWidth = cellGoldTextPaint.measureText(cell.subText)
                        canvas.drawText(cell.subText, (currentX + scoreColWidth - subWidth - 4).toFloat(), (currentY + rowHeight - 6).toFloat(), cellGoldTextPaint)
                    }
                } else {
                    // PLAYER_SUB: Single centered gross score
                    val xCenterF = xCenter.toFloat()
                    val yCenterF = (currentY + 33).toFloat()
                    drawBadgeOnCanvas(canvas, xCenterF, yCenterF, 16f, relation, cellPaint)
                    
                    val gPaint = android.graphics.Paint(cellPaint).apply { textSize = 20f }
                    val gStr = g.toString()
                    val gWidth = gPaint.measureText(gStr)
                    canvas.drawText(gStr, xCenterF - (gWidth / 2), yCenterF + 7, gPaint)
                    
                    // Draw a small dot if dots won
                    if (hasDots) {
                        val dotPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#E65100")
                            style = android.graphics.Paint.Style.FILL
                            isAntiAlias = true
                        }
                        canvas.drawCircle(xCenterF + 23f, yCenterF - 20f, 3.5f, dotPaint)
                    }
                }
            } else if (cell.subText.isNotEmpty()) {
                // Draw diagonal line from bottom-left to top-right of cell
                canvas.drawLine(
                    currentX.toFloat(),
                    (currentY + rowHeight).toFloat(),
                    (currentX + scoreColWidth).toFloat(),
                    currentY.toFloat(),
                    borderPaint
                )
                
                // Draw main score in top-left
                cellPaint.textSize = 17f
                canvas.drawText(cell.mainText, (currentX + 6).toFloat(), (currentY + 24).toFloat(), cellPaint)
                
                // Draw sub text in bottom-right
                cellGoldTextPaint.textSize = 14f
                val subWidth = cellGoldTextPaint.measureText(cell.subText)
                canvas.drawText(cell.subText, (currentX + scoreColWidth - subWidth - 6).toFloat(), (currentY + rowHeight - 8).toFloat(), cellGoldTextPaint)
            } else {
                // Centered single line of text
                cellPaint.textSize = 22f
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

@Composable
fun GolfScoreBadge(
    score: Int,
    par: Int,
    textColor: Color,
    hasDots: Boolean = false,
    modifier: Modifier = Modifier
) {
    val relation = score - par
    Box(
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center
    ) {
        if (score > 0) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val badgeColor = textColor
                val strokeWidth = 3f
                val badgeSize = 32.dp.toPx()
                val center = this.center

                if (hasDots) {
                    drawCircle(
                        color = Color(0xFFFFB300),
                        radius = 2.5f.dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset(x = this.size.width - 2.dp.toPx(), y = 2.dp.toPx())
                    )
                }

                when {
                    relation == -1 -> {
                        // Birdie: Single Circle
                        drawCircle(
                            color = badgeColor,
                            radius = badgeSize / 2f - 2.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    }
                    relation <= -2 -> {
                        // Eagle: Double Circle
                        drawCircle(
                            color = badgeColor,
                            radius = badgeSize / 2f - 2.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                        drawCircle(
                            color = badgeColor,
                            radius = badgeSize / 2f - 6.dp.toPx(),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    }
                    relation == 1 -> {
                        // Bogey: Single Square
                        val rectSize = badgeSize - 4.dp.toPx()
                        drawRect(
                            color = badgeColor,
                            topLeft = androidx.compose.ui.geometry.Offset(center.x - rectSize/2f, center.y - rectSize/2f),
                            size = androidx.compose.ui.geometry.Size(rectSize, rectSize),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    }
                    relation >= 2 -> {
                        // Double Bogey or worse: Double Square
                        val rectSize1 = badgeSize - 4.dp.toPx()
                        drawRect(
                            color = badgeColor,
                            topLeft = androidx.compose.ui.geometry.Offset(center.x - rectSize1/2f, center.y - rectSize1/2f),
                            size = androidx.compose.ui.geometry.Size(rectSize1, rectSize1),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                        val rectSize2 = badgeSize - 12.dp.toPx()
                        drawRect(
                            color = badgeColor,
                            topLeft = androidx.compose.ui.geometry.Offset(center.x - rectSize2/2f, center.y - rectSize2/2f),
                            size = androidx.compose.ui.geometry.Size(rectSize2, rectSize2),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                        )
                    }
                }
            }
            Text(
                text = "$score",
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text("-", color = Color.Gray, fontSize = 16.sp)
        }
    }
}

fun drawBadgeOnCanvas(
    canvas: android.graphics.Canvas,
    x: Float,
    y: Float,
    r: Float,
    relation: Int,
    basePaint: android.graphics.Paint
) {
    val badgePaint = android.graphics.Paint().apply {
        color = basePaint.color
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    when {
        relation == -1 -> {
            // Birdie: Single Circle
            canvas.drawCircle(x, y - 1, r, badgePaint)
        }
        relation <= -2 -> {
            // Eagle: Double Circle
            canvas.drawCircle(x, y - 1, r, badgePaint)
            canvas.drawCircle(x, y - 1, r - 3.5f, badgePaint)
        }
        relation == 1 -> {
            // Bogey: Single Square
            canvas.drawRect(x - r, y - r - 1, x + r, y + r - 1, badgePaint)
        }
        relation >= 2 -> {
            // Double Bogey or worse: Double Square
            canvas.drawRect(x - r, y - r - 1, x + r, y + r - 1, badgePaint)
            canvas.drawRect(x - r + 3.5f, y - r - 1 + 3.5f, x + r - 3.5f, y + r - 1 - 3.5f, badgePaint)
        }
    }
}

fun getPlayerDotsBreakdown(
    player: Player,
    h: Int,
    scores: Map<String, List<HoleScore>>,
    breakdownMatrix: Map<String, List<HoleCalculationResult>>,
    course: Course?,
    teams: List<Team>,
    useTeams: Boolean,
    pressHoles: List<Int>
): List<String> {
    val dotsList = mutableListOf<String>()
    val par = course?.holePars?.getOrNull(h - 1) ?: 4
    val playerHoleScore = scores[player.id]?.getOrNull(h - 1) ?: HoleScore()
    if (playerHoleScore.grossScore <= 0) return emptyList()

    val multiplier = if (pressHoles.isEmpty()) 1 else {
        var m = 1
        pressHoles.sorted().forEach { pHole ->
            if (h >= pHole) m *= 2
        }
        m
    }

    if (useTeams && teams.isNotEmpty()) {
        val team = teams.find { it.playerIds.contains(player.id) }
        val wonSkin = if (team != null) {
            isPlayerHoleWinnerForTeam(player, h - 1, scores, breakdownMatrix, teams)
        } else false
        
        if (wonSkin) {
            val teamCalc = breakdownMatrix[team?.id]?.getOrNull(h - 1) ?: HoleCalculationResult()
            dotsList.add("Skins (Hole Won): +${teamCalc.skinDotsWon} dot${if (teamCalc.skinDotsWon > 1) "s" else ""}")
        }
        if (par == 3 && playerHoleScore.hasCtp) {
            val ownerId = team?.id ?: player.id
            val hCalc = breakdownMatrix[ownerId]?.getOrNull(h - 1) ?: HoleCalculationResult()
            if (hCalc.ctpDotsWon > 0) {
                dotsList.add("Closest to the Pin (CTP): +${hCalc.ctpDotsWon} dot${if (hCalc.ctpDotsWon > 1) "s" else ""}")
            }
        }
        
        val scoreRelationToPar = playerHoleScore.grossScore - par
        if (scoreRelationToPar == -1) {
            dotsList.add("Birdie: +${2 * multiplier} dots")
        } else if (scoreRelationToPar <= -2) {
            dotsList.add("Eagle or Better: +${5 * multiplier} dots")
        }
        
        val madeParOrBetter = playerHoleScore.grossScore <= par
        if (madeParOrBetter) {
            if (playerHoleScore.hasDrive) dotsList.add("Drive: +${1 * multiplier} dot")
            if (playerHoleScore.hasSandy) dotsList.add("Sandy: +${1 * multiplier} dot")
            if (playerHoleScore.hasJacques) dotsList.add("Jacques: +${1 * multiplier} dot")
            if (playerHoleScore.hasLostBoy) dotsList.add("Lost Boy: +${1 * multiplier} dot")
        }
        if (playerHoleScore.hasPoley) dotsList.add("Poley: +${1 * multiplier} dot")
        if (playerHoleScore.hasDoublePoley) dotsList.add("Double Poley: +${2 * multiplier} dots")
    } else {
        // Individual mode
        val hCalc = breakdownMatrix[player.id]?.getOrNull(h - 1) ?: HoleCalculationResult()
        
        if (hCalc.skinDotsWon > 0) {
            dotsList.add("Skins (Hole Won): +${hCalc.skinDotsWon} dot${if (hCalc.skinDotsWon > 1) "s" else ""}")
        }
        if (par == 3 && playerHoleScore.hasCtp && hCalc.ctpDotsWon > 0) {
            dotsList.add("Closest to the Pin (CTP): +${hCalc.ctpDotsWon} dot${if (hCalc.ctpDotsWon > 1) "s" else ""}")
        }
        
        val scoreRelationToPar = playerHoleScore.grossScore - par
        if (scoreRelationToPar == -1) {
            dotsList.add("Birdie: +${2 * multiplier} dots")
        } else if (scoreRelationToPar <= -2) {
            dotsList.add("Eagle or Better: +${5 * multiplier} dots")
        }
        
        val madeParOrBetter = playerHoleScore.grossScore <= par
        if (madeParOrBetter) {
            if (playerHoleScore.hasDrive) dotsList.add("Drive: +${1 * multiplier} dot")
            if (playerHoleScore.hasSandy) dotsList.add("Sandy: +${1 * multiplier} dot")
            if (playerHoleScore.hasJacques) dotsList.add("Jacques: +${1 * multiplier} dot")
            if (playerHoleScore.hasLostBoy) dotsList.add("Lost Boy: +${1 * multiplier} dot")
        }
        if (playerHoleScore.hasPoley) dotsList.add("Poley: +${1 * multiplier} dot")
        if (playerHoleScore.hasDoublePoley) dotsList.add("Double Poley: +${2 * multiplier} dots")
    }

    return dotsList
}

fun playerEarnedDotsOnHole(hScore: HoleScore, par: Int): Boolean {
    if (hScore.grossScore <= 0) return false
    val scoreRelationToPar = hScore.grossScore - par
    if (scoreRelationToPar < 0) return true // Birdie/Eagle
    if (hScore.hasPoley || hScore.hasDoublePoley) return true
    
    val madeParOrBetter = hScore.grossScore <= par
    if (madeParOrBetter && (hScore.hasDrive || hScore.hasSandy || hScore.hasJacques || hScore.hasLostBoy)) return true
    if (par == 3 && hScore.hasCtp) return true
    return false
}

fun isPlayerHoleWinnerForTeam(
    player: Player?,
    hIndex: Int,
    scores: Map<String, List<HoleScore>>,
    breakdownMatrix: Map<String, List<HoleCalculationResult>>,
    teams: List<Team>
): Boolean {
    if (player == null) return false
    val team = teams.find { it.playerIds.contains(player.id) } ?: return false
    val teamCalc = breakdownMatrix[team.id]?.getOrNull(hIndex) ?: HoleCalculationResult()
    if (teamCalc.skinDotsWon <= 0) return false
    
    val teamPlayersHoleScores = team.playerIds.map { pId ->
        pId to (scores[pId]?.getOrNull(hIndex)?.netScore ?: Int.MAX_VALUE)
    }.filter { (scores[it.first]?.getOrNull(hIndex)?.grossScore ?: 0) > 0 }
    
    if (teamPlayersHoleScores.isEmpty()) return false
    val minNet = teamPlayersHoleScores.map { it.second }.minOrNull() ?: Int.MAX_VALUE
    val playerHoleScore = scores[player.id]?.getOrNull(hIndex) ?: HoleScore()
    return playerHoleScore.grossScore > 0 && playerHoleScore.netScore == minNet
}