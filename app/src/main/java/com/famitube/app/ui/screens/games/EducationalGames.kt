package com.famitube.app.ui.screens.games

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.famitube.app.ui.theme.*

enum class GameAgeGroup(val label: String, val ageRange: String) {
    TODDLER("🧸 Toddlers", "Ages 2–4"),
    YOUNG_KIDS("🎒 Young Kids", "Ages 5–8"),
    OLDER_KIDS("🚀 Older Kids", "Ages 9–12")
}

@Composable
fun EducationalGamesScreen(
    currentAgeFilter: String,
    starBalance: Int,
    onAwardStars: (Int, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeAgeGroup = when (currentAgeFilter.uppercase()) {
        "TODDLER" -> GameAgeGroup.TODDLER
        "YOUNG_KIDS" -> GameAgeGroup.YOUNG_KIDS
        else -> GameAgeGroup.OLDER_KIDS
    }

    var selectedGameIndex by remember { mutableIntStateOf(0) }

    // Reset game index when parent changes age filter
    LaunchedEffect(activeAgeGroup) {
        selectedGameIndex = 0
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Reward & Motivation Hero Banner (locked to child's assigned age tier)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Learning Quests 🎮",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PinkPrimary.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${activeAgeGroup.label} (${activeAgeGroup.ageRange})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = PinkPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Play free educational games, solve puzzles & earn ⭐ stars!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2C2408))
                            .border(1.2.dp, Color(0xFFFFD166), RoundedCornerShape(20.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "⭐ $starBalance",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFD166),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // 2. Games Tabs for Child's Profile Age Group
        item {
            val gamesForGroup = when (activeAgeGroup) {
                GameAgeGroup.TODDLER -> listOf("🦁 Animal Sounds", "🎈 Balloon Pop", "🎨 Shape Sorter")
                GameAgeGroup.YOUNG_KIDS -> listOf("⚡ Math Sprint", "🃏 Memory Match", "🔤 Spelling Quest")
                GameAgeGroup.OLDER_KIDS -> listOf("🧠 Science Trivia", "🌍 World Explorer", "🌌 Orbit Physics", "💡 Logic Lab")
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(gamesForGroup) { index, title ->
                    val isSelected = selectedGameIndex == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) CyanAccent else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { selectedGameIndex = index }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // 3. Active Game Area
        item {
            when (activeAgeGroup) {
                GameAgeGroup.TODDLER -> {
                    when (selectedGameIndex) {
                        0 -> ToddlerAnimalMatchGame(onAwardStars = onAwardStars)
                        1 -> ToddlerBalloonPopGame(onAwardStars = onAwardStars)
                        else -> ToddlerShapeSorterGame(onAwardStars = onAwardStars)
                    }
                }
                GameAgeGroup.YOUNG_KIDS -> {
                    when (selectedGameIndex) {
                        0 -> YoungKidsMathSprintGame(onAwardStars = onAwardStars)
                        1 -> YoungKidsMemoryMatchGame(onAwardStars = onAwardStars)
                        else -> YoungKidsSpellingQuestGame(onAwardStars = onAwardStars)
                    }
                }
                GameAgeGroup.OLDER_KIDS -> {
                    when (selectedGameIndex) {
                        0 -> OlderKidsScienceTriviaGame(onAwardStars = onAwardStars)
                        1 -> OlderKidsWorldExplorerGame(onAwardStars = onAwardStars)
                        2 -> OlderKidsOrbitPhysicsGame(onAwardStars = onAwardStars)
                        else -> OlderKidsLogicLabGame(onAwardStars = onAwardStars)
                    }
                }
            }
        }
    }
}

// ==========================================
// TODDLER GAMES (Ages 2-4)
// ==========================================

data class AnimalPrompt(val name: String, val emoji: String, val sound: String, val fact: String)

@Composable
fun ToddlerAnimalMatchGame(onAwardStars: (Int, String) -> Unit) {
    val animals = remember {
        listOf(
            AnimalPrompt("Lion", "🦁", "ROOOAR! 🦁", "Lions live in family groups called prides!"),
            AnimalPrompt("Cow", "🐮", "MOOOOO! 🐮", "Cows eat grass and make healthy milk!"),
            AnimalPrompt("Duck", "🦆", "QUACK QUACK! 🦆", "Ducks have waterproof feathers to swim!"),
            AnimalPrompt("Frog", "🐸", "RIBBIT RIBBIT! 🐸", "Frogs can jump up to 20 times their body length!"),
            AnimalPrompt("Elephant", "🐘", "PAWOOO! 🐘", "Elephants have long trunks to drink and play!"),
            AnimalPrompt("Dog", "🐶", "WOOF WOOF! 🐶", "Dogs are loyal and love playing fetch!")
        )
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }

    val current = animals[currentIndex % animals.size]
    val options = remember(currentIndex) {
        val wrong = animals.filter { it.name != current.name }.shuffled().take(2)
        (listOf(current) + wrong).shuffled()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🦁 Animal Sounds & Picture Match",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = BlueSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 24.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "Who says: \"${current.sound}\"?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD166),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                options.forEach { opt ->
                    val isCorrect = opt.name == current.name
                    val isSelected = selectedAnswer == opt.name
                    val bg = when {
                        !answered -> MaterialTheme.colorScheme.surfaceVariant
                        isCorrect -> Color(0xFF06D6A0)
                        isSelected -> Color(0xFFEF476F)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = !answered) {
                                selectedAnswer = opt.name
                                answered = true
                                if (isCorrect) onAwardStars(10, "Animal Match: ${opt.name}")
                            },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = bg)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = opt.emoji, fontSize = 42.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = opt.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (answered && (isCorrect || isSelected)) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(16.dp))
                val isCorrect = selectedAnswer == current.name
                Text(
                    text = if (isCorrect) "🎉 Yay! The ${current.name} says ${current.sound}! +10 ⭐" else "That's a ${selectedAnswer}! Try the next animal!",
                    color = if (isCorrect) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        answered = false
                        selectedAnswer = null
                        currentIndex++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Next Animal →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ToddlerBalloonPopGame(onAwardStars: (Int, String) -> Unit) {
    var poppedList by remember { mutableStateOf(setOf<Int>()) }
    var currentTarget by remember { mutableIntStateOf(1) }
    var isRoundComplete by remember { mutableStateOf(false) }

    val balloons = remember {
        listOf(
            Triple(1, "🎈", Color(0xFFFF2E93)),
            Triple(2, "🎈", Color(0xFF00B4D8)),
            Triple(3, "🎈", Color(0xFF06D6A0)),
            Triple(4, "🎈", Color(0xFFFFD166)),
            Triple(5, "🎈", Color(0xFF8338EC))
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🎈 Balloon Pop Counting (1 to 5)",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PinkPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (!isRoundComplete) "Pop balloon number: $currentTarget" else "🎉 You popped all 5 balloons! +10 ⭐",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (!isRoundComplete) CyanAccent else Color(0xFF06D6A0)
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                balloons.forEach { (num, icon, color) ->
                    val isPopped = num in poppedList
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isPopped) Color.Transparent else color.copy(alpha = 0.25f))
                            .border(
                                width = if (isPopped) 0.dp else 2.dp,
                                color = if (isPopped) Color.Transparent else color,
                                shape = CircleShape
                            )
                            .clickable(enabled = !isPopped && num == currentTarget) {
                                poppedList = poppedList + num
                                if (num == 5) {
                                    isRoundComplete = true
                                    onAwardStars(10, "Balloon Pop Counting Champion")
                                } else {
                                    currentTarget = num + 1
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isPopped) "💥" else "$num",
                            fontSize = if (isPopped) 26.sp else 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPopped) Color.Unspecified else color
                        )
                    }
                }
            }

            if (isRoundComplete) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        poppedList = emptySet()
                        currentTarget = 1
                        isRoundComplete = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Play Again 🔄", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class ShapeTarget(val name: String, val icon: String, val colorName: String, val colorHex: Color)

@Composable
fun ToddlerShapeSorterGame(onAwardStars: (Int, String) -> Unit) {
    val shapes = remember {
        listOf(
            ShapeTarget("Red Circle", "🔴", "Red", Color(0xFFEF476F)),
            ShapeTarget("Blue Square", "🟦", "Blue", Color(0xFF00B4D8)),
            ShapeTarget("Green Triangle", "🔺", "Green", Color(0xFF06D6A0)),
            ShapeTarget("Yellow Star", "⭐", "Yellow", Color(0xFFFFD166))
        )
    }

    var targetIndex by remember { mutableIntStateOf(0) }
    var answered by remember { mutableStateOf(false) }
    var selectedShape by remember { mutableStateOf<String?>(null) }

    val current = shapes[targetIndex % shapes.size]

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🎨 Shape & Color Sorter",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MintSuccess
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "Find the: ${current.name}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = current.colorHex
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                shapes.forEach { s ->
                    val isCorrect = s.name == current.name
                    val isSelected = selectedShape == s.name
                    val bg = when {
                        !answered -> MaterialTheme.colorScheme.surfaceVariant
                        isCorrect -> Color(0xFF06D6A0).copy(alpha = 0.4f)
                        isSelected -> Color(0xFFEF476F).copy(alpha = 0.4f)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .border(
                                width = if (answered && isCorrect) 2.dp else 1.dp,
                                color = if (answered && isCorrect) Color(0xFF06D6A0) else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable(enabled = !answered) {
                                selectedShape = s.name
                                answered = true
                                if (isCorrect) onAwardStars(10, "Shape Sorter: ${s.name}")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = s.icon, fontSize = 32.sp)
                    }
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(16.dp))
                val isCorrect = selectedShape == current.name
                Text(
                    text = if (isCorrect) "🌟 Great job! That's the ${current.name}! +10 ⭐" else "That was the $selectedShape! Try the next one!",
                    color = if (isCorrect) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        answered = false
                        selectedShape = null
                        targetIndex++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Shape →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// YOUNG KIDS GAMES (Ages 5-8)
// ==========================================

@Composable
fun YoungKidsMathSprintGame(onAwardStars: (Int, String) -> Unit) {
    var a by remember { mutableIntStateOf(7) }
    var b by remember { mutableIntStateOf(5) }
    var isPlus by remember { mutableStateOf(true) }
    var answered by remember { mutableStateOf(false) }
    var selectedAns by remember { mutableStateOf<Int?>(null) }

    val correct = if (isPlus) a + b else a - b
    val options = remember(a, b, isPlus) {
        listOf(correct, correct - 2, correct + 3, correct + 1).distinct().shuffled()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "⚡ Math Balloon Sprint",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD166)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$a ${if (isPlus) "+" else "-"} $b = ?",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { opt ->
                    val isCorr = opt == correct
                    val isSel = selectedAns == opt
                    val bg = when {
                        !answered -> MaterialTheme.colorScheme.surfaceVariant
                        isCorr -> Color(0xFF06D6A0)
                        isSel -> Color(0xFFEF476F)
                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .clickable(enabled = !answered) {
                                selectedAns = opt
                                answered = true
                                if (isCorr) onAwardStars(10, "Math Sprint: $a ${if (isPlus) "+" else "-"} $b")
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$opt",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (answered && (isCorr || isSel)) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(14.dp))
                val isCorr = selectedAns == correct
                Text(
                    text = if (isCorr) "⭐ Brilliant! +10 Stars added!" else "Good try! $a ${if (isPlus) "+" else "-"} $b = $correct.",
                    color = if (isCorr) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        answered = false
                        selectedAns = null
                        isPlus = (0..1).random() == 1
                        if (isPlus) {
                            a = (4..12).random()
                            b = (3..9).random()
                        } else {
                            a = (8..18).random()
                            b = (2..a - 1).random()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Problem →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun YoungKidsMemoryMatchGame(onAwardStars: (Int, String) -> Unit) {
    val items = remember { listOf("🚀", "🚀", "🦁", "🦁", "🐬", "🐬", "🦖", "🦖").shuffled() }
    var flipped by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var matched by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var firstPick by remember { mutableStateOf<Int?>(null) }
    var isAwarded by remember { mutableStateOf(false) }

    LaunchedEffect(matched.size) {
        if (matched.size == 8 && !isAwarded) {
            isAwarded = true
            onAwardStars(15, "Memory Match Quest 8-Pack")
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🃏 Memory Match • Find 4 Pairs",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CyanAccent
                )
                Text(
                    text = "Pairs: ${matched.size / 2}/4",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0..3) {
                            val idx = row * 4 + col
                            val isFlipped = idx in flipped || idx in matched
                            val isMatch = idx in matched

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            isMatch -> Color(0xFF06D6A0).copy(alpha = 0.35f)
                                            isFlipped -> Color(0xFF202636)
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isMatch) Color(0xFF06D6A0) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable(enabled = !isFlipped && firstPick != idx) {
                                        if (firstPick == null) {
                                            firstPick = idx
                                            flipped = setOf(idx)
                                        } else {
                                            val prev = firstPick!!
                                            flipped = setOf(prev, idx)
                                            firstPick = null
                                            if (items[prev] == items[idx]) {
                                                matched = matched + setOf(prev, idx)
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isFlipped) items[idx] else "❓",
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }

            if (matched.size == 8) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "🎉 Incredible Memory! All 4 pairs matched! +15 ⭐ Stars awarded!",
                    color = Color(0xFF06D6A0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

data class SpellingWord(val word: String, val clue: String, val icon: String)

@Composable
fun YoungKidsSpellingQuestGame(onAwardStars: (Int, String) -> Unit) {
    val words = remember {
        listOf(
            SpellingWord("SUN", "Shines bright in the daytime", "☀️"),
            SpellingWord("BOAT", "Floats and sails on water", "⛵"),
            SpellingWord("FISH", "Swims in rivers and oceans", "🐟"),
            SpellingWord("TREE", "Has green leaves and roots", "🌳"),
            SpellingWord("BIRD", "Has feathers and can fly", "🐦")
        )
    }

    var wordIndex by remember { mutableIntStateOf(0) }
    var currentLetters by remember { mutableStateOf(listOf<String>()) }
    var isSuccess by remember { mutableStateOf(false) }

    val current = words[wordIndex % words.size]
    val targetWord = current.word
    val pool = remember(wordIndex) { targetWord.map { it.toString() }.shuffled() }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🔤 Phonics & Spelling Quest",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PinkPrimary
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = current.icon, fontSize = 48.sp)
            Text(text = current.clue, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(14.dp))

            // Current spelled word box
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in targetWord.indices) {
                    val letter = currentLetters.getOrNull(i) ?: "_"
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (letter != "_") BlueSecondary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = letter,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (letter != "_") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Letter pool buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pool.forEachIndexed { idx, char ->
                    val usedCountInSpelled = currentLetters.count { it == char }
                    val totalInPool = pool.take(idx + 1).count { it == char }
                    val isUsed = totalInPool <= usedCountInSpelled

                    Button(
                        onClick = {
                            if (!isUsed && currentLetters.size < targetWord.length) {
                                val next = currentLetters + char
                                currentLetters = next
                                if (next.joinToString("") == targetWord) {
                                    isSuccess = true
                                    onAwardStars(10, "Spelling Quest: $targetWord")
                                }
                            }
                        },
                        enabled = !isUsed && !isSuccess,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(char, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }

            if (isSuccess) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "🎉 Awesome! You spelled $targetWord! +10 ⭐",
                    color = Color(0xFF06D6A0),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        isSuccess = false
                        currentLetters = emptyList()
                        wordIndex++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Word →", fontWeight = FontWeight.Bold)
                }
            } else if (currentLetters.size == targetWord.length) {
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { currentLetters = emptyList() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF476F)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Try Again 🔄")
                }
            }
        }
    }
}

// ==========================================
// OLDER KIDS GAMES (Ages 9-12)
// ==========================================

data class OlderTrivia(val q: String, val opts: List<String>, val ans: String, val fact: String)

@Composable
fun OlderKidsScienceTriviaGame(onAwardStars: (Int, String) -> Unit) {
    val questions = remember {
        listOf(
            OlderTrivia("What is the speed of light in a vacuum?", listOf("300,000 km/s", "150,000 km/s", "1,000,000 km/s", "88,000 km/s"), "300,000 km/s", "Light travels from the Sun to Earth in just 8 minutes and 20 seconds!"),
            OlderTrivia("Which organelle is known as the powerhouse of the cell?", listOf("Nucleus", "Mitochondria", "Ribosome", "Chloroplast"), "Mitochondria", "Mitochondria generate ATP, which cells use for energy!"),
            OlderTrivia("What is the most abundant gas in Earth's atmosphere?", listOf("Oxygen", "Nitrogen", "Carbon Dioxide", "Argon"), "Nitrogen", "Nitrogen makes up roughly 78% of Earth's atmosphere!"),
            OlderTrivia("Which element has the chemical symbol 'Au' on the periodic table?", listOf("Silver", "Gold", "Iron", "Copper"), "Gold", "From the Latin word 'Aurum', meaning shining dawn!"),
            OlderTrivia("How many bones are in the adult human body?", listOf("186", "206", "246", "300"), "206", "Babies are born with around 300 bones, which fuse as they grow!")
        )
    }

    var qIdx by remember { mutableIntStateOf(0) }
    var selectedOpt by remember { mutableStateOf<String?>(null) }
    var answered by remember { mutableStateOf(false) }

    val q = questions[qIdx % questions.size]

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🧠 Science & Space Trivia • Question ${(qIdx % questions.size) + 1}/${questions.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = BlueSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = q.q,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            q.opts.forEach { opt ->
                val isCorrect = opt == q.ans
                val isSel = selectedOpt == opt
                val bg = when {
                    !answered -> MaterialTheme.colorScheme.surfaceVariant
                    isCorrect -> Color(0xFF06D6A0)
                    isSel -> Color(0xFFEF476F)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable(enabled = !answered) {
                            selectedOpt = opt
                            answered = true
                            if (isCorrect) onAwardStars(10, "Science Trivia: ${q.q}")
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = opt,
                        fontSize = 14.sp,
                        fontWeight = if (isSel || (answered && isCorrect)) FontWeight.Bold else FontWeight.Medium,
                        color = if (answered && (isCorrect || isSel)) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(12.dp))
                val isCorrect = selectedOpt == q.ans
                Text(
                    text = if (isCorrect) "🎉 Correct! +10 ⭐ Stars added!" else "Not quite! The correct answer is ${q.ans}.",
                    color = if (isCorrect) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "💡 ${q.fact}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        answered = false
                        selectedOpt = null
                        qIdx++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Next Science Question →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class GeoQuestion(val prompt: String, val options: List<String>, val answer: String, val landmark: String)

@Composable
fun OlderKidsWorldExplorerGame(onAwardStars: (Int, String) -> Unit) {
    val questions = remember {
        listOf(
            GeoQuestion("Which is the longest river in the world?", listOf("Amazon", "Nile", "Yangtze", "Mississippi"), "Nile", "The Nile runs over 6,650 kilometers through northeastern Africa!"),
            GeoQuestion("What is the capital city of Japan?", listOf("Seoul", "Tokyo", "Kyoto", "Osaka"), "Tokyo", "Tokyo is the world's most populous metropolitan area!"),
            GeoQuestion("On which continent can you visit the Sahara Desert?", listOf("Asia", "Africa", "Australia", "South America"), "Africa", "The Sahara Desert is nearly the size of the entire United States!"),
            GeoQuestion("Which country is home to the ancient monument Machu Picchu?", listOf("Peru", "Chile", "Brazil", "Mexico"), "Peru", "Machu Picchu sits high in the Andes mountains, built by the Incas in the 15th century!"),
            GeoQuestion("What is the smallest continent by land area?", listOf("Europe", "Antarctica", "Australia", "South America"), "Australia", "Australia is both a continent and a country surrounded by oceans!")
        )
    }

    var geoIdx by remember { mutableIntStateOf(0) }
    var selectedAnswer by remember { mutableStateOf<String?>(null) }
    var answered by remember { mutableStateOf(false) }

    val current = questions[geoIdx % questions.size]

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = "🌍 World Explorer & Geography Quest • ${(geoIdx % questions.size) + 1}/${questions.size}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MintSuccess
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = current.prompt,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            current.options.forEach { opt ->
                val isCorrect = opt == current.answer
                val isSel = selectedAnswer == opt
                val bg = when {
                    !answered -> MaterialTheme.colorScheme.surfaceVariant
                    isCorrect -> Color(0xFF06D6A0)
                    isSel -> Color(0xFFEF476F)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .clickable(enabled = !answered) {
                            selectedAnswer = opt
                            answered = true
                            if (isCorrect) onAwardStars(15, "World Geography: ${current.prompt}")
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = opt,
                        fontSize = 14.sp,
                        fontWeight = if (isSel || (answered && isCorrect)) FontWeight.Bold else FontWeight.Medium,
                        color = if (answered && (isCorrect || isSel)) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (answered) {
                Spacer(modifier = Modifier.height(12.dp))
                val isCorrect = selectedAnswer == current.answer
                Text(
                    text = if (isCorrect) "🌟 Explorer Badge! Correct! +15 ⭐" else "Almost! The answer was ${current.answer}.",
                    color = if (isCorrect) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "🗺️ ${current.landmark}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = {
                        answered = false
                        selectedAnswer = null
                        geoIdx++
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PinkPrimary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Explore Next Destination →", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun OlderKidsOrbitPhysicsGame(onAwardStars: (Int, String) -> Unit) {
    var velocityKmS by remember { mutableFloatStateOf(7.8f) }
    var launched by remember { mutableStateOf(false) }
    var outcome by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🌌 Gravity & Orbit Physics Lab",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = CyanAccent
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Launch a satellite from Low Earth Orbit. Find the right orbital velocity (~7.6 - 8.0 km/s) for a stable orbit!",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Planetary Orbit Visual Stage
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF090D16)),
                contentAlignment = Alignment.Center
            ) {
                // Earth
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0077B6)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🌍", fontSize = 28.sp)
                }

                // Orbital ring
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .border(
                            width = 1.dp,
                            color = if (isSuccess) Color(0xFF06D6A0) else Color(0xFF4CC9F0).copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )

                // Satellite
                Box(
                    modifier = Modifier
                        .offset(x = 52.dp, y = (-20).dp)
                ) {
                    Text("🛰️", fontSize = 18.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Launch Velocity: ${String.format("%.1f", velocityKmS)} km/s",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFFFFD166)
            )

            Slider(
                value = velocityKmS,
                onValueChange = {
                    velocityKmS = it
                    launched = false
                    outcome = null
                },
                valueRange = 5.0f..11.5f,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    launched = true
                    when {
                        velocityKmS < 7.4f -> {
                            outcome = "💥 Gravity pulled the satellite into Earth's atmosphere! Velocity too slow."
                            isSuccess = false
                        }
                        velocityKmS > 8.4f -> {
                            outcome = "🚀 Satellite reached escape trajectory and flew into deep space! Velocity too fast."
                            isSuccess = false
                        }
                        else -> {
                            outcome = "🎉 Perfect Orbital Insertion! The satellite achieved stable orbit! +20 ⭐"
                            isSuccess = true
                            onAwardStars(20, "Orbital Mechanics Physicist")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isSuccess) Color(0xFF06D6A0) else PinkPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isSuccess) "Orbit Stable ✓" else "Launch Satellite 🚀", fontWeight = FontWeight.Bold)
            }

            if (outcome != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = outcome!!,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSuccess) Color(0xFF06D6A0) else Color(0xFFEF476F),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun OlderKidsLogicLabGame(onAwardStars: (Int, String) -> Unit) {
    var switchA by remember { mutableStateOf(false) }
    var switchB by remember { mutableStateOf(true) }
    var gateType by remember { mutableStateOf("AND") } // AND, OR
    var hasSolved by remember { mutableStateOf(false) }

    val output = when (gateType) {
        "AND" -> switchA && switchB
        "OR" -> switchA || switchB
        else -> false
    }

    LaunchedEffect(output) {
        if (output && !hasSolved) {
            hasSolved = true
            onAwardStars(15, "Logic Gate Circuit Engineer")
        }
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "💡 Logic Circuit Lab • Power the Generator",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD166)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Toggle the inputs and gate so output light turns ON (True)!",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            // Circuit Visual
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { switchA = !switchA },
                        colors = ButtonDefaults.buttonColors(containerColor = if (switchA) Color(0xFF06D6A0) else Color(0xFF475569)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Input A: ${if (switchA) "ON (1)" else "OFF (0)"}", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { switchB = !switchB },
                        colors = ButtonDefaults.buttonColors(containerColor = if (switchB) Color(0xFF06D6A0) else Color(0xFF475569)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Input B: ${if (switchB) "ON (1)" else "OFF (0)"}", fontSize = 12.sp)
                    }
                }

                // Gate Selector
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BlueSecondary)
                        .clickable { gateType = if (gateType == "AND") "OR" else "AND" }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("[$gateType Gate]", fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Generator Bulb Output
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (output) "💡" else "⚫", fontSize = 36.sp)
                    Text(
                        text = if (output) "POWER ON!" else "DARK",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (output) Color(0xFF06D6A0) else Color(0xFF94A3B8)
                    )
                }
            }

            if (output) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "🎉 Generator Powered! Circuit Complete! +15 ⭐",
                    color = Color(0xFF06D6A0),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
