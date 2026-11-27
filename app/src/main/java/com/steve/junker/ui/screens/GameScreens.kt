package com.steve.junker.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import com.steve.junker.data.models.GameType
import com.steve.junker.data.models.HoleScore
import com.steve.junker.data.models.Player
import com.steve.junker.data.models.Course
import com.steve.junker.data.models.Team
import com.steve.junker.ui.viewmodel.GameViewModel
import com.steve.junker.data.models.calculateJunkDots
import com.steve.junker.data.models.calculateNinesPoints
import com.steve.junker.voice.OfflineSpeechHelper
import com.steve.junker.voice.VoiceParseResult
import com.steve.junker.voice.parseGolfTranscript
import com.steve.junker.DrawerHeaderBar

val GolfGreen = Color(0xFF1B5E20)
val GoldAccent = Color(0xFFFFD700)
val LightGold = Color(0xFFC5A000)

@Composable
fun getAppBg(dark: Boolean) = if (dark) Color(0xFF121212) else Color(0xFFF5F5F5)
@Composable
fun getCardBg(dark: Boolean) = if (dark) Color(0xFF1E1E1E) else Color(0xFFFFFFFF)
@Composable
fun getTextPrimary(dark: Boolean) = if (dark) Color.White else Color(0xFF1C1C1C)
@Composable
fun getTextSecondary(dark: Boolean) = if (dark) Color.Gray else Color(0xFF444444)
@Composable
fun getGoldThemeColor(dark: Boolean) = if (dark) GoldAccent else LightGold

@Composable
fun JunkerLaunchScreen(onTimeout: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }
    Surface(modifier = Modifier.fillMaxSize(), color = GolfGreen) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("JUNKER", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, letterSpacing = 6.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Side-Action Scorecard Matrix", fontSize = 14.sp, color = GoldAccent, fontWeight = FontWeight.Medium, letterSpacing = 2.sp)
        }
    }
}

@Composable
fun PlayerSetupScreen(
    viewModel: GameViewModel, 
    onMenuClick: () -> Unit,
    onSetupComplete: () -> Unit
) {
    var newPlayerName by remember { mutableStateOf("") }
    var newPlayerHandicap by remember { mutableStateOf("") }
    
    val savedPlayers by viewModel.savedPlayers.collectAsState()
    val isPlayersLoading by viewModel.isPlayersLoading.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useTeams by viewModel.useTeams.collectAsState()
    val gameType by viewModel.gameType.collectAsState()
    val isBlitzEnabled by viewModel.isBlitzEnabled.collectAsState()
    val pointValue by viewModel.pointValue.collectAsState()
    var customPointValueText by remember {
        mutableStateOf(
            if (pointValue == 0.0) "0"
            else if (pointValue == pointValue.toLong().toDouble()) pointValue.toLong().toString()
            else String.format(java.util.Locale.US, "%.2f", pointValue)
        )
    }
    
    val activeFoursome = remember { mutableStateListOf<Player>() }
    var errorMessage by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        viewModel.refreshPlayers()
    }

    LaunchedEffect(gameType) {
        if (gameType == GameType.NINES && useTeams) {
            viewModel.setUseTeams(false)
        }
    }

    val appBg = getAppBg(isDarkMode)
    val cardBg = getCardBg(isDarkMode)
    val textPrimary = getTextPrimary(isDarkMode)
    val textSecondary = getTextSecondary(isDarkMode)
    val goldColor = getGoldThemeColor(isDarkMode)

    Surface(modifier = Modifier.fillMaxSize().imePadding(), color = appBg) {
        Column(modifier = Modifier.padding(24.dp)) {
            DrawerHeaderBar("Player Directory", isDarkMode, onMenuClick, { viewModel.toggleTheme() })

            // Game Format Selector (Raw Junker vs Nines)
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isRawJunker = gameType == GameType.RAW_JUNKER
                Button(
                    onClick = {
                        viewModel.setGameType(GameType.RAW_JUNKER)
                        errorMessage = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRawJunker) GolfGreen else cardBg,
                        contentColor = if (isRawJunker) Color.White else textPrimary
                    ),
                    border = if (isRawJunker) null else BorderStroke(1.dp, Color.Gray),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Text("Raw Junker", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        viewModel.setGameType(GameType.NINES)
                        viewModel.setUseTeams(false)
                        errorMessage = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isRawJunker) GolfGreen else cardBg,
                        contentColor = if (!isRawJunker) Color.White else textPrimary
                    ),
                    border = if (!isRawJunker) null else BorderStroke(1.dp, Color.Gray),
                    modifier = Modifier.weight(1f).height(42.dp)
                ) {
                    Text("Nines", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            Text(
                if (gameType == GameType.NINES) "Select or create players for Nines (3 or 4)"
                else "Select or create players for the match (1 to 4)",
                style = MaterialTheme.typography.bodyMedium,
                color = textSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (gameType == GameType.RAW_JUNKER) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useTeams, onCheckedChange = { viewModel.setUseTeams(it) })
                    Text("Enable 2v2 Team Scoring Mode", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isBlitzEnabled, onCheckedChange = { viewModel.setBlitzEnabled(it) })
                    Text("Enable Blitz Variant (2+ stroke win takes all points)", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Point / Dot Value ($) Selector
            Text("Point / Dot Value ($)", style = MaterialTheme.typography.titleSmall, color = textSecondary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val presets = listOf(0.0 to "$0", 0.5 to "$0.50", 1.0 to "$1", 2.0 to "$2", 5.0 to "$5")
                presets.forEach { (presetVal, label) ->
                    val isSelected = kotlin.math.abs(pointValue - presetVal) < 0.001
                    OutlinedButton(
                        onClick = {
                            viewModel.setPointValue(presetVal)
                            customPointValueText = if (presetVal == 0.0) "0" else if (presetVal == presetVal.toLong().toDouble()) presetVal.toLong().toString() else String.format(java.util.Locale.US, "%.2f", presetVal)
                        },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) GolfGreen else Color.Transparent,
                            contentColor = if (isSelected) Color.White else textPrimary
                        ),
                        border = BorderStroke(1.dp, if (isSelected) GolfGreen else Color.Gray),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedTextField(
                    value = customPointValueText,
                    onValueChange = { raw ->
                        val cleaned = raw.filter { it.isDigit() || it == '.' }
                        if (cleaned.count { it == '.' } <= 1) {
                            customPointValueText = cleaned
                            cleaned.toDoubleOrNull()?.let { viewModel.setPointValue(it) }
                        }
                    },
                    label = { Text("$", fontSize = 11.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(74.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GolfGreen,
                        unfocusedBorderColor = Color.Gray,
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newPlayerName,
                    onValueChange = { input ->
                        newPlayerName = if (input.isEmpty()) "" else input.substring(0, 1).uppercase() + input.substring(1).lowercase()
                    },
                    label = { Text("New Name") },
                    modifier = Modifier.weight(1.5f),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GolfGreen, unfocusedBorderColor = Color.Gray, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                )

                OutlinedTextField(
                    value = newPlayerHandicap,
                    onValueChange = { if (it.isEmpty() || (it.all { c -> c.isDigit() } && it.length <= 2)) newPlayerHandicap = it },
                    label = { Text("HCP") },
                    modifier = Modifier.weight(0.8f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GolfGreen, unfocusedBorderColor = Color.Gray, focusedTextColor = textPrimary, unfocusedTextColor = textPrimary)
                )

                Button(
                    onClick = {
                        val hcpParsed = newPlayerHandicap.toIntOrNull()
                        if (newPlayerName.isBlank()) errorMessage = "Enter a valid name."
                        else if (hcpParsed == null || hcpParsed !in 0..54) errorMessage = "Enter handicap (0-54)."
                        else {
                            viewModel.savePlayerToDb(Player(java.util.UUID.randomUUID().toString(), newPlayerName.trim(), hcpParsed))
                            newPlayerName = ""
                            newPlayerHandicap = ""
                            errorMessage = ""
                            focusManager.clearFocus()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = goldColor),
                    modifier = Modifier.height(56.dp)
                ) {
                    Text("Create", color = if (isDarkMode) Color.Black else Color.White, fontWeight = FontWeight.Bold)
                }
            }

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Active Match Queue (${activeFoursome.size}/4):", style = MaterialTheme.typography.titleSmall, color = goldColor)
            
            Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().height(65.dp).padding(vertical = 4.dp)) {
                Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (activeFoursome.isEmpty()) Text("Tap profiles below to queue...", color = textSecondary)
                    else activeFoursome.forEach { p ->
                        SuggestionChip(onClick = { activeFoursome.remove(p) }, label = { Text("${p.name} (${p.handicap})", color = Color.White) }, colors = SuggestionChipDefaults.suggestionChipColors(containerColor = GolfGreen))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            if (useTeams && activeFoursome.size == 4) {
                Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Teams Configuration Layout:", color = goldColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Team 1: ${activeFoursome.getOrNull(0)?.name} / ${activeFoursome.getOrNull(1)?.name}", color = textPrimary, fontSize = 11.sp)
                            Text("Team 2: ${activeFoursome.getOrNull(2)?.name} / ${activeFoursome.getOrNull(3)?.name}", color = textPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Saved Directory Profiles:", style = MaterialTheme.typography.titleSmall, color = textSecondary)
                    if (isPlayersLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = GolfGreen)
                    }
                }
                IconButton(
                    onClick = { viewModel.refreshPlayers() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("🔄", fontSize = 14.sp)
                }
            }
            
            if (savedPlayers.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (isPlayersLoading) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = GolfGreen)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Loading players directory...", color = textSecondary, fontSize = 14.sp)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            Text("No players found in directory", color = textPrimary, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Create a new player above or tap 🔄 to reload.", color = textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(savedPlayers) { player ->
                        val isSelected = activeFoursome.any { it.id == player.id }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (isSelected) (if (isDarkMode) Color(0xFF2E2E2E) else Color(0xFFE0E0E0)) else cardBg),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                                if (isSelected) activeFoursome.removeAll { it.id == player.id }
                                else if (activeFoursome.size < 4) activeFoursome.add(player)
                            }
                        ) {
                            Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text(player.name, style = MaterialTheme.typography.titleMedium, color = textPrimary)
                                    Text("Handicap: ${player.handicap}", style = MaterialTheme.typography.bodySmall, color = textSecondary)
                                }
                                IconButton(onClick = { viewModel.deletePlayerFromDb(player) }) { Text("✕", color = Color.Red, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    if (gameType == GameType.NINES && activeFoursome.size !in 3..4) {
                        errorMessage = "Nines requires 3 or 4 players."
                    } else if (activeFoursome.isEmpty()) {
                        errorMessage = "Queue players to start matching."
                    } else if (gameType == GameType.RAW_JUNKER && useTeams && activeFoursome.size != 4) {
                        errorMessage = "Team mode requires exactly 4 queued players."
                    } else {
                        var builtTeamsList = emptyList<Team>()
                        val effectiveUseTeams = gameType == GameType.RAW_JUNKER && useTeams
                        if (effectiveUseTeams) {
                            val t1 = Team("team_1", "${activeFoursome[0].name} / ${activeFoursome[1].name}", listOf(activeFoursome[0].id, activeFoursome[1].id))
                            val t2 = Team("team_2", "${activeFoursome[2].name} / ${activeFoursome[3].name}", listOf(activeFoursome[2].id, activeFoursome[3].id))
                            builtTeamsList = listOf(t1, t2)
                            viewModel.configureTeams(builtTeamsList)
                        }
                        viewModel.setupGame(
                            players = activeFoursome.toList(),
                            course = Course("", "", emptyList(), emptyList()),
                            matchInTeamMode = effectiveUseTeams,
                            activeTeamsList = builtTeamsList,
                            gameType = gameType,
                            isBlitzEnabled = if (gameType == GameType.NINES) isBlitzEnabled else false,
                            pointValue = pointValue
                        )
                        onSetupComplete()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GolfGreen),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text("Confirm & Select Course", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun CourseSetupScreen(viewModel: GameViewModel, onMenuClick: () -> Unit, onCourseCreated: (Course) -> Unit) {
    var courseName by remember { mutableStateOf("") }
    var apiSearchQuery by remember { mutableStateOf("") }
    
    val holeHandicaps = remember { mutableStateListOf(*Array(18) { "" }) }
    val holePars = remember { mutableStateListOf(*Array(18) { "4" }) }
    
    val savedCourses by viewModel.savedCourses.collectAsState()
    val isCoursesLoading by viewModel.isCoursesLoading.collectAsState()
    val apiSearchResults by viewModel.apiSearchResults.collectAsState()
    val isApiLoading by viewModel.isApiLoading.collectAsState()
    val apiErrorMessage by viewModel.apiErrorMessage.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    var localErrorMessage by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.refreshCourses()
    }

    val appBg = getAppBg(isDarkMode)
    val cardBg = getCardBg(isDarkMode)
    val textPrimary = getTextPrimary(isDarkMode)
    val textSecondary = getTextSecondary(isDarkMode)
    val goldColor = getGoldThemeColor(isDarkMode)

    Surface(modifier = Modifier.fillMaxSize().imePadding(), color = appBg) {
        LazyColumn(modifier = Modifier.padding(24.dp)) {
            item {
                DrawerHeaderBar("Course Setup", isDarkMode, onMenuClick, null)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("Search Online Database:", style = MaterialTheme.typography.titleMedium, color = goldColor)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = apiSearchQuery,
                        onValueChange = { apiSearchQuery = it },
                        label = { Text("Course or Club Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GolfGreen, focusedTextColor = textPrimary)
                    )
                    Button(
                        onClick = { viewModel.searchRemoteCourses(apiSearchQuery) },
                        colors = ButtonDefaults.buttonColors(containerColor = GolfGreen),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("Search", color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (isApiLoading) {
                item { LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), color = goldColor) }
            }
            if (apiErrorMessage.isNotEmpty()) {
                item { 
                    Text(apiErrorMessage, color = Color.Red, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) 
                    Button(onClick = { viewModel.clearApiError() }) { Text("Dismiss") }
                }
            }

            if (apiSearchResults.isNotEmpty() && !isApiLoading) {
                items(apiSearchResults) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = GolfGreen.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable {
                            viewModel.fetchAndSaveRemoteCourse(item.id) { downloadedCourse ->
                                onCourseCreated(downloadedCourse)
                            }
                        }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val displayCourseName: String = item.course_name ?: "Unknown Course"
                            val displayClubName: String = item.club_name ?: ""
                            Text(displayCourseName, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(displayClubName, color = textSecondary, fontSize = 12.sp)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(12.dp)); Divider(color = textSecondary) }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select a Locally Saved Course:", style = MaterialTheme.typography.titleMedium, color = goldColor)
                    if (isCoursesLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = GolfGreen)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (savedCourses.isEmpty()) {
                item { Text("No local profiles found.", color = textSecondary) }
            } else {
                items(savedCourses) { course ->
                    Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onCourseCreated(course) }) {
                        Text(text = course.name, modifier = Modifier.padding(16.dp), color = textPrimary, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = textSecondary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Create Custom Manual Course Profile:", style = MaterialTheme.typography.titleMedium, color = goldColor)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = courseName, onValueChange = { courseName = it }, label = { Text("Course Name") }, modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = GolfGreen, focusedTextColor = textPrimary)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            items(18) { index ->
                Card(colors = CardDefaults.cardColors(containerColor = cardBg), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Hole ${index + 1}", color = textPrimary, modifier = Modifier.width(60.dp), fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = holeHandicaps[index], onValueChange = { if (it.isEmpty() || (it.all { c -> c.isDigit() } && it.length <= 2)) holeHandicaps[index] = it },
                            label = { Text("HCP") }, modifier = Modifier.weight(1.5f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary)
                        )
                        OutlinedTextField(
                            value = holePars[index], onValueChange = { if (it.isEmpty() || (it.all { c -> c.isDigit() } && it.length <= 1)) holePars[index] = it },
                            label = { Text("Par") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = textPrimary)
                        )
                    }
                }
            }

            item {
                if (localErrorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(localErrorMessage, color = Color.Red, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        val parsedHandicaps = holeHandicaps.map { it.toIntOrNull() }
                        val parsedPars = holePars.map { it.toIntOrNull() }
                        if (courseName.isBlank()) localErrorMessage = "Please enter a course name."
                        else if (parsedHandicaps.any { it == null || it !in 1..18 }) localErrorMessage = "Holes must have valid handicap (1-18)."
                        else if (parsedPars.any { it == null || it !in 3..6 }) localErrorMessage = "Holes must have valid par (3-6)."
                        else {
                            val newCourse = Course(id = java.util.UUID.randomUUID().toString(), name = courseName.trim(), holeHandicaps = parsedHandicaps.filterNotNull(), holePars = parsedPars.filterNotNull())
                            viewModel.saveCourseToDb(newCourse)
                            onCourseCreated(newCourse)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GolfGreen), modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Save & Select Course", color = Color.White) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScorecardScreen(viewModel: GameViewModel, onMenuClick: () -> Unit, onNavigateToLeaderboard: () -> Unit) {
    val currentHole by viewModel.currentHole.collectAsState()
    val players by viewModel.activePlayers.collectAsState()
    val scores by viewModel.scores.collectAsState()
    val pressHoles by viewModel.pressHoles.collectAsState()
    val course by viewModel.selectedCourse.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val useTeams by viewModel.useTeams.collectAsState()
    val teams by viewModel.teams.collectAsState()
    val gameType by viewModel.gameType.collectAsState()
    val isBlitzEnabled by viewModel.isBlitzEnabled.collectAsState()
    val isRoundLocked by viewModel.isRoundLocked.collectAsState()
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val latestHole by rememberUpdatedState(currentHole)
    val latestPlayers by rememberUpdatedState(players)
    var pendingVoiceResult by remember { mutableStateOf<VoiceParseResult?>(null) }

    val speechHelper = remember {
        OfflineSpeechHelper(context) { transcript ->
            pendingVoiceResult = parseGolfTranscript(transcript, latestHole, latestPlayers)
        }
    }

    val isListening by speechHelper.isListening.collectAsState()
    val partialTranscript by speechHelper.partialTranscript.collectAsState()
    val speechError by speechHelper.errorMessage.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            speechHelper.startListening(latestPlayers.map { it.name })
        } else {
            Toast.makeText(context, "Microphone permission is required for voice scoring.", Toast.LENGTH_SHORT).show()
        }
    }

    val startVoiceCapture: () -> Unit = {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            speechHelper.startListening(latestPlayers.map { it.name })
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        onDispose { speechHelper.destroy() }
    }

    val holePar = course?.holePars?.getOrNull(currentHole - 1) ?: 4
    val holeHcp = course?.holeHandicaps?.getOrNull(currentHole - 1) ?: 18

    val appBg = getAppBg(isDarkMode)
    val textPrimary = getTextPrimary(isDarkMode)
    val textSecondary = getTextSecondary(isDarkMode)
    val goldColor = getGoldThemeColor(isDarkMode)

    val triggerAutoSave: () -> Unit = {
        if (gameType == GameType.NINES) {
            val ninesResult = calculateNinesPoints(scores, players, isBlitzEnabled)
            viewModel.archiveCurrentRound(ninesResult.playerTotals)
        } else {
            val calculatedMatrix = calculateJunkDots(scores, pressHoles, course, teams, useTeams)
            viewModel.archiveCurrentRound(calculatedMatrix.entityTotals)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = appBg) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick, modifier = Modifier.size(48.dp)) {
                    Text("☰", fontSize = 28.sp, color = textPrimary, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { 
                    if (currentHole > 1) { 
                        triggerAutoSave()
                        viewModel.setCurrentHole(currentHole - 1) 
                        coroutineScope.launch { listState.scrollToItem(0) }
                    } 
                }) { 
                    Text("<", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp) 
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Hole $currentHole", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                    Text("Par $holePar — HCP $holeHcp", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                }
                IconButton(onClick = { 
                    if (currentHole < 18) { 
                        triggerAutoSave()
                        viewModel.setCurrentHole(currentHole + 1) 
                        coroutineScope.launch { listState.scrollToItem(0) }
                    } 
                }) { 
                    Text(">", color = textPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp) 
                }
                Spacer(modifier = Modifier.width(56.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isListening) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = GolfGreen.copy(alpha = 0.15f)),
                    border = BorderStroke(1.5.dp, GolfGreen),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🎤 Listening...", color = GolfGreen, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                            TextButton(onClick = { speechHelper.stopListening() }) {
                                Text("Done / Cancel", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        if (partialTranscript.isNotBlank()) {
                            Text(
                                text = "\"$partialTranscript\"",
                                color = textPrimary,
                                fontStyle = FontStyle.Italic,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            } else {
                Button(
                    onClick = startVoiceCapture,
                    enabled = !isRoundLocked,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkMode) Color(0xFF263238) else Color(0xFFE8F5E9),
                        contentColor = if (isDarkMode) GoldAccent else GolfGreen,
                        disabledContainerColor = Color.Gray.copy(alpha = 0.2f)
                    ),
                    border = BorderStroke(1.dp, if (isRoundLocked) Color.Gray else GolfGreen),
                    modifier = Modifier.fillMaxWidth().height(44.dp).padding(bottom = 0.dp)
                ) {
                    Text("🎤 Speak Scores", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                }
                if (!speechError.isNullOrBlank()) {
                    Text(
                        text = speechError!!,
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (gameType == GameType.RAW_JUNKER) {
                val currentMultiplier = if (pressHoles.isEmpty()) 1 else {
                    var m = 1
                    pressHoles.sorted().forEach { pHole ->
                        if (currentHole >= pHole) m *= 2
                    }
                    m
                }

                if (pressHoles.contains(currentHole)) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.fillMaxWidth()) {
                        Text("🔴 PRESS CALLED ON THIS HOLE (x$currentMultiplier)", color = Color.White, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                } else {
                    val hasPressActive = pressHoles.isNotEmpty()
                    val isBeforeFirstPress = hasPressActive && currentHole < pressHoles.min()
                    
                    if (isBeforeFirstPress) {
                        // Disabled "until the hole where it was activated"
                        Button(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.buttonColors(containerColor = goldColor.copy(alpha = 0.5f), disabledContainerColor = goldColor.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text("Call Press Match", color = if (isDarkMode) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    } else {
                        if (currentMultiplier > 1) {
                            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE65100)), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Text("🔥 PRESS ACTIVE — DOTS MULTIPLIER: x$currentMultiplier", color = Color.White, modifier = Modifier.fillMaxWidth().padding(8.dp), fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        Button(
                            onClick = { viewModel.callPress(currentHole); triggerAutoSave() }, 
                            enabled = !isRoundLocked,
                            colors = ButtonDefaults.buttonColors(containerColor = goldColor, disabledContainerColor = goldColor.copy(alpha = 0.5f)), 
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            val btnText = if (currentMultiplier > 1) "Call Additional Press (x${currentMultiplier * 2})" else "Call Press Match"
                            Text(btnText, color = if (isDarkMode) Color.Black else Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (gameType == GameType.RAW_JUNKER && useTeams && teams.size == 2) {
                    teams.forEach { team ->
                        item {
                            Box(modifier = Modifier.fillMaxWidth().background(GolfGreen.copy(alpha = 0.25f)).padding(vertical = 8.dp, horizontal = 12.dp)) {
                                Text("🏆 Team: ${team.name}", color = if(isDarkMode) GoldAccent else GolfGreen, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            }
                        }
                        items(team.playerIds) { pId ->
                            val player = players.find { it.id == pId }
                            if (player != null) {
                                val playerHoleScores = scores[player.id] ?: List(18) { HoleScore() }
                                val currentHoleScore = playerHoleScores.getOrNull(currentHole - 1) ?: HoleScore()
                                PlayerScoreRow(player, currentHoleScore, holePar, isDarkMode, !isRoundLocked, showJunkDots = true, { 
                                    viewModel.updateGrossScore(player.id, currentHole, it)
                                    triggerAutoSave() 
                                }, { 
                                    viewModel.toggleJunkDot(player.id, currentHole, it)
                                    triggerAutoSave() 
                                })
                            }
                        }
                    }
                } else {
                    items(players) { player ->
                        val playerHoleScores = scores[player.id] ?: List(18) { HoleScore() }
                        val currentHoleScore = playerHoleScores.getOrNull(currentHole - 1) ?: HoleScore()
                        PlayerScoreRow(player, currentHoleScore, holePar, isDarkMode, !isRoundLocked, showJunkDots = (gameType == GameType.RAW_JUNKER), { 
                            viewModel.updateGrossScore(player.id, currentHole, it)
                            triggerAutoSave()
                        }, { 
                            viewModel.toggleJunkDot(player.id, currentHole, it)
                            triggerAutoSave()
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { triggerAutoSave(); onNavigateToLeaderboard() }, 
                colors = ButtonDefaults.buttonColors(containerColor = GolfGreen), 
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("View Match Leaderboard", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    pendingVoiceResult?.let { result ->
        ModalBottomSheet(
            onDismissRequest = { pendingVoiceResult = null },
            containerColor = getCardBg(isDarkMode)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Proposed Updates for Hole ${result.targetHole}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = textPrimary
                )
                Text(
                    text = "\"${result.rawTranscript}\"",
                    fontSize = 13.sp,
                    fontStyle = FontStyle.Italic,
                    color = textSecondary
                )
                HorizontalDivider(color = textSecondary.copy(alpha = 0.25f))

                if (!result.isValid) {
                    Text(
                        text = "Could not recognize any player scores or junk dots. Try saying: 'Mark 5, Steve 4, Keith double poley'",
                        color = Color.Red,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pendingVoiceResult = null },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Cancel", color = textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                pendingVoiceResult = null
                                startVoiceCapture()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GolfGreen),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Try Again", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    result.updates.forEach { update ->
                        val visibleDots = if (gameType == GameType.NINES) emptySet() else update.junkDots
                        Card(
                            colors = CardDefaults.cardColors(containerColor = appBg),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = update.playerName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (update.grossScore != null) {
                                        Box(
                                            modifier = Modifier
                                                .background(GolfGreen, RoundedCornerShape(6.dp))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Score: ${update.grossScore}",
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                    visibleDots.forEach { dot ->
                                        Box(
                                            modifier = Modifier
                                                .background(goldColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = formatDotLabel(dot),
                                                color = goldColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pendingVoiceResult = null },
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("Cancel", color = textPrimary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                viewModel.applyVoiceBatch(result) {
                                    coroutineScope.launch { listState.scrollToItem(0) }
                                }
                                pendingVoiceResult = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GolfGreen),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) {
                            Text("Apply to Scorecard", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

private fun formatDotLabel(dotKey: String): String = when (dotKey) {
    "hasDrive" -> "Drive"
    "hasSandy" -> "Sandy"
    "hasDoubleSandy" -> "Dbl Sandy"
    "hasPoley" -> "Poley"
    "hasDoublePoley" -> "Dbl Poley"
    "hasJacques" -> "Jacques"
    "hasLostBoy" -> "Lost Boy"
    "hasCtp" -> "CTP"
    else -> dotKey
}

@Composable
fun PlayerScoreRow(
    player: Player, holeScore: HoleScore, holePar: Int, isDarkMode: Boolean,
    enabled: Boolean,
    showJunkDots: Boolean = true,
    onGrossChange: (Int) -> Unit, onDotToggle: (String) -> Unit
) {
    val cardBg = getCardBg(isDarkMode)
    val textPrimary = getTextPrimary(isDarkMode)
    val textSecondary = getTextSecondary(isDarkMode)
    val goldColor = getGoldThemeColor(isDarkMode)
    val scrollState = rememberScrollState()

    Card(colors = CardDefaults.cardColors(containerColor = cardBg), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(player.name, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = textPrimary)
                Box(modifier = Modifier.background(if (isDarkMode) Color(0xFF2C2C2C) else Color(0xFFE8F5E9)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Text(text = "NET: ${if (holeScore.grossScore > 0) holeScore.netScore else "-"}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = goldColor)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                Text("Select Gross Score:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (scoreOption in 1..8) {
                        val isSelected = holeScore.grossScore == scoreOption
                        OutlinedButton(
                            onClick = { onGrossChange(scoreOption) },
                            enabled = enabled,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (isSelected) GolfGreen else Color.Transparent, 
                                contentColor = if (isSelected) Color.White else textPrimary,
                                disabledContainerColor = if (isSelected) GolfGreen.copy(alpha = 0.5f) else Color.Transparent,
                                disabledContentColor = if (isSelected) Color.White.copy(alpha = 0.5f) else textPrimary.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.5.dp, if (isSelected) GolfGreen else textSecondary.copy(alpha = 0.6f)),
                            modifier = Modifier.size(width = 46.dp, height = 42.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) { Text(text = "$scoreOption", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold) }
                    }
                }
            }
            if (showJunkDots) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select Side Junk Dots:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.weight(1f)) { DotCheckbox("Drive", holeScore.hasDrive, textPrimary, enabled) { onDotToggle("hasDrive") } }
                        Box(modifier = Modifier.weight(1f)) { DotCheckbox("Sandy", holeScore.hasSandy, textPrimary, enabled) { onDotToggle("hasSandy") } }
                        Box(modifier = Modifier.weight(1f)) { DotCheckbox("Dbl Sandy", holeScore.hasDoubleSandy, textPrimary, enabled) { onDotToggle("hasDoubleSandy") } }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.weight(1f)) { DotCheckbox("Poley", holeScore.hasPoley, textPrimary, enabled) { onDotToggle("hasPoley") } }
                        Box(modifier = Modifier.weight(1f)) { DotCheckbox("Dbl Poley", holeScore.hasDoublePoley, textPrimary, enabled) { onDotToggle("hasDoublePoley") } }
                        Box(modifier = Modifier.weight(1f)) { DotCheckbox("Jacques", holeScore.hasJacques, textPrimary, enabled) { onDotToggle("hasJacques") } }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Box(modifier = Modifier.weight(1f)) { DotCheckbox("LostBoy", holeScore.hasLostBoy, textPrimary, enabled) { onDotToggle("hasLostBoy") } }
                        if (holePar == 3) {
                            Box(modifier = Modifier.weight(2f)) { DotCheckbox("🔴 CTP (Closest to Pin)", holeScore.hasCtp, textPrimary, enabled) { onDotToggle("hasCtp") } }
                        } else {
                            Spacer(modifier = Modifier.weight(2f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DotCheckbox(label: String, checked: Boolean, textPrimary: Color, enabled: Boolean, onCheckedChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable(enabled = enabled) { onCheckedChange() }.padding(vertical = 2.dp)) {
        Checkbox(
            checked = checked, 
            onCheckedChange = { if (enabled) onCheckedChange() }, 
            enabled = enabled,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, color = if (enabled) textPrimary else textPrimary.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

fun Modifier.scaleModifier(scale: Float): Modifier = this.then(Modifier.padding(4.dp))