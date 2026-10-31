package com.chessifyy.android

import android.os.Bundle
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

private val Ink = Color(0xFF171A18)
private val Panel = Color(0xFF222723)
private val PanelRaised = Color(0xFF2B322D)
private val Green = Color(0xFF83B64A)
private val GreenDark = Color(0xFF34472B)
private val Cream = Color(0xFFE8E4D8)
private val Muted = Color(0xFFA9B0A8)

enum class GameMode { LOCAL, COMPUTER }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChessifyyApp() }
    }
}

@Composable
private fun ChessifyyApp() {
    var selected by remember { mutableStateOf("Play") }
    var activePuzzle by remember { mutableStateOf("mate-g7") }
    var activePuzzleIsDaily by remember { mutableStateOf(false) }
    var activeLesson by remember { mutableStateOf("opening-basics") }
    var setupMinutes by remember { mutableIntStateOf(10) }
    var setupBotStyle by remember { mutableStateOf(BotStyle.SAGE) }
    var setupHumanSide by remember { mutableStateOf(Side.WHITE) }
    var reviewReturnRoute by remember { mutableStateOf("More") }
    val context = LocalContext.current
    val store = remember(context) { ProgressStore(context) }
    var gameReviews by remember(store) { mutableStateOf(store.recentGameReviews()) }
    val onReviewSaved: (GameReviewData) -> Unit = { review ->
        store.saveLastGameReview(review)
        gameReviews = store.recentGameReviews()
        reviewReturnRoute = if (selected == "TournamentGame") "Tournaments" else "Play"
        selected = "GameReview"
    }
    MaterialTheme(colorScheme = darkColorScheme(
        background = Ink, surface = Panel, primary = Green, onPrimary = Ink,
        onBackground = Cream, onSurface = Cream, secondary = Muted
    )) {
        Surface(Modifier.fillMaxSize(), color = Ink) {
            if (selected == "Game") {
                ChessGameScreen(mode = GameMode.LOCAL, clockMinutes = setupMinutes, onExit = { selected = "Play" },
                    onReviewSaved = onReviewSaved, onViewReview = { selected = "GameReview" })
            } else if (selected == "BotGame") {
                ChessGameScreen(mode = GameMode.COMPUTER, clockMinutes = setupMinutes, botStyle = setupBotStyle, humanSide = setupHumanSide, onExit = { selected = "Play" },
                    onReviewSaved = onReviewSaved, onViewReview = { selected = "GameReview" })
            } else if (selected == "TournamentGame") {
                ChessGameScreen(mode = GameMode.COMPUTER, clockMinutes = 10, botStyle = setupBotStyle, humanSide = setupHumanSide,
                    onExit = { selected = "Tournaments" }, onGameFinished = { score -> store.recordTournamentGame(score) },
                    onReviewSaved = onReviewSaved, onViewReview = { selected = "GameReview" })
            } else Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("♞", color = Green, fontSize = 28.sp)
                        Text(" chessifyy", color = Cream, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    }
                    Text("♙  Guest", color = Muted, fontSize = 14.sp)
                }
                Box(Modifier.weight(1f)) {
                    when (selected) {
                        "Play" -> PlayHome(store = store, onOpen = {
                            if (it == "DailyPuzzle") {
                                activePuzzle = puzzleOfTheDay().id
                                activePuzzleIsDaily = true
                                selected = "Puzzle"
                            } else selected = it
                        })
                        "GameSetup", "LocalGameSetup" -> GameSetupPage(
                            initialMode = if (selected == "LocalGameSetup") GameMode.LOCAL else GameMode.COMPUTER,
                            onBack = { selected = "Play" },
                            initialBotStyle = setupBotStyle,
                            onStart = { mode, minutes, botStyle, humanSide ->
                                setupMinutes = minutes
                                setupBotStyle = botStyle
                                setupHumanSide = humanSide
                                selected = if (mode == GameMode.COMPUTER) "BotGame" else "Game"
                            }
                        )
                        "Puzzles" -> PuzzleHub(store = store, onBack = { selected = "Play" }, onPuzzle = { id, daily -> activePuzzle = id; activePuzzleIsDaily = daily; selected = "Puzzle" })
                        "Puzzle" -> PuzzleScreen(puzzleId = activePuzzle, daily = activePuzzleIsDaily, store = store, onBack = { selected = "Puzzles" })
                        "Learn" -> LessonHub(store = store, onBack = { selected = "Play" }, onLesson = { id -> activeLesson = id; selected = "Lesson" })
                        "Lesson" -> LessonScreen(lessonId = activeLesson, store = store, onBack = { selected = "Learn" })
                        "Watch" -> FeaturePage("Watch", "Follow chess learning and events", listOf("Game broadcasts", "Featured games", "Event calendar"))
                        "More" -> MoreHub(onOpen = { if (it == "GameReview") reviewReturnRoute = "More"; selected = it })
                        "Ratings" -> RatingsPage(store = store, onBack = { selected = "More" })
                        "Analysis" -> AnalysisPage(onBack = { selected = "More" })
                        "GameReview" -> GameReviewPage(recentGames = gameReviews, onBack = { selected = reviewReturnRoute }, onNewGame = { selected = "GameSetup" })
                        "Tournaments" -> TournamentPage(store = store, onBack = { selected = "More" }, onPlay = { selected = "TournamentGame" })
                        "Clubs" -> ClubsPage(store = store, onBack = { selected = "More" })
                        "Chat" -> ChatPage(store = store, onBack = { selected = "More" })
                        "Online" -> OnlinePlayPage(onBack = { selected = "Play" })
                        else -> FeaturePage(selected, "This Chessifyy flow is next in the build plan", listOf("Choose a time control", "Choose your side", "Start a game"))
                    }
                }
                BottomBar(selected = selected, onSelect = { selected = it })
            }
        }
    }
}

@Composable
private fun PlayHome(store: ProgressStore, onOpen: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(top = 4.dp)) {
                Text("Your next move", color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("Play, learn, and improve. Always free.", color = Muted, fontSize = 14.sp)
            }
        }
        item { BoardPreview() }
        item {
            Button(
                onClick = { onOpen("GameSetup") },
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("Play a game", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("♟", "Play bot", "Choose a strength", Modifier.weight(1f)) { onOpen("GameSetup") }
                ActionCard("⚡", "Quick match", "Play online", Modifier.weight(1f)) { onOpen("Online") }
            }
        }
        item {
            SectionTitle("Improve your game")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("♞", "Daily puzzle", "Keep your streak", Modifier.weight(1f)) { onOpen("DailyPuzzle") }
                ActionCard("▤", "Lessons", "Learn at your pace", Modifier.weight(1f)) { onOpen("Learn") }
            }
        }
        item {
            SectionTitle("Play together")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionCard("♙", "Two players", "Share this device", Modifier.weight(1f)) { onOpen("LocalGameSetup") }
                ActionCard("🏆", "Tournaments", "Join an event", Modifier.weight(1f)) { onOpen("Tournaments") }
            }
        }
        item { Text("Ratings", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Rapid" to store.rapidRating().toString(), "Blitz" to store.blitzRating().toString(), "Bullet" to store.bulletRating().toString()).forEach { (label, score) ->
                    Column(Modifier.weight(1f).background(Panel, RoundedCornerShape(10.dp)).padding(12.dp)) {
                        Text(score, color = Cream, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        Text(label, color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun BoardPreview() {
    val rows = listOf("♜♞♝♛♚♝♞♜", "♟♟♟♟♟♟♟♟", "········", "········", "········", "········", "♙♙♙♙♙♙♙♙", "♖♘♗♕♔♗♘♖")
    Column(Modifier.fillMaxWidth().aspectRatio(1f).background(Panel, RoundedCornerShape(12.dp)).padding(8.dp)) {
        rows.forEachIndexed { r, row ->
            Row(Modifier.weight(1f)) {
                row.forEachIndexed { c, piece ->
                    val light = (r + c) % 2 == 0
                    Box(
                        Modifier.weight(1f).fillMaxSize().background(if (light) Color(0xFFD9D5C7) else Color(0xFF76905B)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (piece != '·') Text(piece.toString(), fontSize = 28.sp, color = if (r < 2) Color(0xFF20221F) else Color(0xFFF7F3E8))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun ActionCard(icon: String, title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.background(Panel, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(icon, color = Green, fontSize = 22.sp)
        Text(title, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(subtitle, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun FeaturePage(title: String, subtitle: String, items: List<String>) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        items.forEach { label ->
            Row(
                Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).clickable { }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, color = Cream, fontSize = 16.sp)
                Text("›", color = Green, fontSize = 25.sp)
            }
        }
    }
}

@Composable
private fun BottomBar(selected: String, onSelect: (String) -> Unit) {
    val items = listOf("Play" to "♟", "Puzzles" to "♘", "Learn" to "▤", "Watch" to "◉", "More" to "•••")
    Row(
        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (label, icon) ->
            val active = selected == label
            Column(
                Modifier.clickable { onSelect(label) }.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
                    if (active) Box(Modifier.size(28.dp).background(GreenDark, CircleShape))
                    Text(icon, color = if (active) Green else Muted, fontSize = 18.sp)
                }
                Text(label, color = if (active) Green else Muted, fontSize = 10.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ChessGameScreen(
    mode: GameMode,
    clockMinutes: Int,
    botStyle: BotStyle = BotStyle.SAGE,
    humanSide: Side = Side.WHITE,
    onExit: () -> Unit,
    onGameFinished: (Double) -> Unit = {},
    onReviewSaved: (GameReviewData) -> Unit = {},
    onViewReview: () -> Unit = {}
) {
    fun newPosition(): ChessGame = ChessGame().apply {
        if (mode == GameMode.COMPUTER && humanSide == Side.BLACK) {
            loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1")
        }
    }
    var game by remember(mode, clockMinutes, humanSide) { mutableStateOf(newPosition()) }
    var revision by remember { mutableIntStateOf(0) }
    var selectedSquare by remember { mutableStateOf<Int?>(null) }
    var flipped by remember(humanSide) { mutableStateOf(mode == GameMode.COMPUTER && humanSide == Side.BLACK) }
    var pendingPromotion by remember { mutableStateOf<List<ChessMove>?>(null) }
    var drawOffer by remember { mutableStateOf(false) }
    var whiteMillis by remember { mutableLongStateOf(clockMinutes * 60 * 1000L) }
    var blackMillis by remember { mutableLongStateOf(clockMinutes * 60 * 1000L) }
    var reviewSaved by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val store = remember(context) { ProgressStore(context) }
    val soundPool = remember(context) {
        SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build()
    }
    val moveSound = remember(soundPool) { soundPool.load(context, R.raw.move, 1) }
    val captureSound = remember(soundPool) { soundPool.load(context, R.raw.capture, 1) }
    val checkSound = remember(soundPool) { soundPool.load(context, R.raw.check, 1) }
    val finishSound = remember(soundPool) { soundPool.load(context, R.raw.finish, 1) }
    DisposableEffect(soundPool) { onDispose { soundPool.release() } }
    var gameStartRating by remember(mode, clockMinutes) { mutableIntStateOf(store.ratingForMinutes(clockMinutes)) }
    val position = game.position()
    val lastMove = game.lastMove

    fun makeMoveWithSound(move: ChessMove) {
        val movingPiece = game.pieces[move.from]
        val isCapture = game.pieces[move.to] != null ||
            (movingPiece?.type == ChessPieceType.PAWN && move.from % 8 != move.to % 8)
        game.makeMove(move)
        val sound = when {
            game.status.finished -> finishSound
            game.position().inCheck -> checkSound
            isCapture -> captureSound
            else -> moveSound
        }
        soundPool.play(sound, 0.55f, 0.55f, 1, 0, 1f)
        revision++
    }

    fun restartGame() {
        game = newPosition()
        whiteMillis = clockMinutes * 60 * 1000L
        blackMillis = clockMinutes * 60 * 1000L
        selectedSquare = null
        pendingPromotion = null
        gameStartRating = store.ratingForMinutes(clockMinutes)
        reviewSaved = false
        revision++
    }

    LaunchedEffect(game, game.sideToMove, game.status.finished) {
        while (!game.status.finished) {
            delay(1000)
            if (game.status.finished) break
            if (game.sideToMove == Side.WHITE) {
                if (whiteMillis <= 1000L) {
                    whiteMillis = 0L
                    game.flagFall(Side.WHITE)
                    revision++
                } else whiteMillis -= 1000L
            } else {
                if (blackMillis <= 1000L) {
                    blackMillis = 0L
                    game.flagFall(Side.BLACK)
                    revision++
                } else blackMillis -= 1000L
            }
            revision++
        }
    }

    LaunchedEffect(game, game.sideToMove, game.status.finished, mode, botStyle) {
        if (mode == GameMode.COMPUTER && game.sideToMove != humanSide && !game.status.finished) {
            delay(350)
            val botTurn = game.sideToMove
            val reply = withContext(Dispatchers.Default) { game.findBestMove(botStyle.searchDepth, botStyle) }
            if (reply != null && game.sideToMove == botTurn && !game.status.finished) {
                makeMoveWithSound(reply)
            }
        }
    }

    LaunchedEffect(revision, game.status.finished, mode, botStyle) {
        if (game.status.finished && !reviewSaved) {
            val score = if (game.status.winner == null) 0.5 else if (game.status.winner == humanSide) 1.0 else 0.0
            if (mode == GameMode.COMPUTER) {
                store.recordComputerGame(score, clockMinutes, botStyle.rating)
                onGameFinished(score)
            }
            val review = GameReviewData(
                positions = game.positionHistory,
                movesUci = game.uciHistory,
                movesSan = game.moveHistory,
                result = game.status.message,
                humanSide = if (mode == GameMode.COMPUTER) humanSide else null,
                baselineRating = gameStartRating,
                opponentRating = if (mode == GameMode.COMPUTER) botStyle.rating else null,
                timeControlMinutes = clockMinutes,
                score = if (mode == GameMode.COMPUTER) score else null
            )
            reviewSaved = true
            onReviewSaved(review)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Ink).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("‹  Back", color = Muted, modifier = Modifier.clickable(onClick = onExit).padding(6.dp))
            Text("Chessifyy", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("New", color = Green, modifier = Modifier.clickable { restartGame() }.padding(6.dp))
        }
        Text(if (mode == GameMode.COMPUTER) "Play ${botStyle.displayName} · ${clockMinutes}+0 · ${humanSide.displayName()}" else "Local game · ${clockMinutes}+0 · take turns on this device",
            color = Muted, fontSize = 13.sp)
        Text(game.status.message, color = if (game.status.finished || position.inCheck) Color(0xFFF2A08C) else Cream,
            fontWeight = FontWeight.SemiBold, fontSize = 17.sp)

        val topSide = if (flipped) Side.WHITE else Side.BLACK
        val bottomSide = topSide.other()
        PlayerStrip(topSide, if (mode == GameMode.COMPUTER) if (topSide == humanSide) "You" else botStyle.displayName else "Local player",
            position.sideToMove == topSide && !game.status.finished,
            clockText(if (topSide == Side.WHITE) whiteMillis else blackMillis))
        key(revision) {
            ChessBoard(
                game = game,
                selectedSquare = selectedSquare,
                flipped = flipped,
                onDrop = { from, to ->
                    if (!game.status.finished && (mode == GameMode.LOCAL || game.sideToMove == humanSide)) {
                        val choices = game.legalMovesFrom(from).filter { it.to == to }
                        if (choices.size > 1 && choices.all { it.promotion != null }) {
                            pendingPromotion = choices
                        } else if (choices.isNotEmpty()) {
                            makeMoveWithSound(choices.first())
                        }
                        selectedSquare = null
                    }
                },
                onTap = { square ->
                    if (!game.status.finished && (mode == GameMode.LOCAL || game.sideToMove == humanSide)) {
                        val from = selectedSquare
                        val choices = from?.let { game.legalMovesFrom(it).filter { move -> move.to == square } }.orEmpty()
                        if (choices.isNotEmpty()) {
                            if (choices.size > 1 && choices.all { it.promotion != null }) {
                                pendingPromotion = choices
                            } else {
                                makeMoveWithSound(choices.first())
                                selectedSquare = null
                            }
                        } else if (game.pieces[square]?.side == game.sideToMove) {
                            selectedSquare = square
                        } else selectedSquare = null
                    }
                }
            )
        }
        PlayerStrip(bottomSide, if (mode == GameMode.COMPUTER) if (bottomSide == humanSide) "You" else botStyle.displayName else "Local player",
            position.sideToMove == bottomSide && !game.status.finished,
            clockText(if (bottomSide == Side.WHITE) whiteMillis else blackMillis))

        Text("Moves", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        val history = game.moveHistory
        val allMovePairs = history.chunked(2)
        val visibleMovePairs = allMovePairs.takeLast(4)
        val firstVisibleMove = allMovePairs.size - visibleMovePairs.size + 1
        Text(
            if (history.isEmpty()) "Make the first move" else visibleMovePairs.mapIndexed { index, pair ->
                val moveNumber = firstVisibleMove + index
                "$moveNumber. ${pair.joinToString("   ")}"
            }.joinToString("     "),
            color = Cream, fontSize = 13.sp, maxLines = 2
        )

        Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                val botHasReplied = mode == GameMode.COMPUTER && game.sideToMove == humanSide && history.size >= 2
                if (game.undo()) {
                    if (botHasReplied) game.undo()
                    revision++
                    selectedSquare = null
                }
            }, enabled = history.isNotEmpty()) { Text("Undo") }
            TextButton(onClick = { flipped = !flipped }) { Text(if (flipped) "White below" else "Flip board") }
            TextButton(onClick = { game.resign(if (mode == GameMode.COMPUTER) humanSide else game.sideToMove); revision++ }, enabled = !game.status.finished) { Text("Resign") }
        }
        if (!game.status.finished) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = { drawOffer = true }) { Text("Offer draw") }
            }
        }
        if (game.status.finished) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextButton(onClick = onViewReview) { Text("Review game") }
                TextButton(onClick = { restartGame() }) { Text("Play again") }
            }
        }
    }

    pendingPromotion?.let { choices ->
        AlertDialog(
            onDismissRequest = { pendingPromotion = null },
            title = { Text("Promote pawn") },
            text = {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    choices.forEach { move ->
                        val type = move.promotion ?: ChessPieceType.QUEEN
                        TextButton(onClick = {
                            makeMoveWithSound(move); pendingPromotion = null; selectedSquare = null
                        }) {
                            Text(if (game.sideToMove == Side.WHITE) type.glyphWhite else type.glyphBlack, fontSize = 30.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pendingPromotion = null }) { Text("Cancel") } }
        )
    }
    if (drawOffer) {
        AlertDialog(
            onDismissRequest = { drawOffer = false },
            title = { Text("Offer a draw?") },
            text = { Text("Both local players must agree to end this game as a draw.") },
            confirmButton = {
                TextButton(onClick = { game.agreeDraw(); drawOffer = false; revision++ }) { Text("Agree draw") }
            },
            dismissButton = { TextButton(onClick = { drawOffer = false }) { Text("Continue") } }
        )
    }
}

@Composable
private fun PlayerStrip(side: Side, name: String, active: Boolean, time: String) {
    Row(
        Modifier.fillMaxWidth().background(if (active) GreenDark else Panel, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${if (side == Side.WHITE) "♙" else "♟"}  ${side.displayName()} · $name", color = Cream, fontSize = 13.sp)
        Text(time, color = if (active) Green else Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

private fun clockText(millis: Long): String {
    val seconds = millis.coerceAtLeast(0L) / 1000L
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private data class PuzzleSpec(val id: String, val title: String, val level: String, val fen: String, val solution: String, val hint: String)
private val puzzles = listOf(
    PuzzleSpec("mate-g7", "The protected queen", "Mate in one", "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1", "f7g7", "Look for a queen move that checks along the diagonal."),
    PuzzleSpec("mate-b7", "Close the escape squares", "Mate in one", "k7/2Q5/2K5/8/8/8/8/8 w - - 0 1", "c7b7", "The king's flight squares matter as much as the check."),
    PuzzleSpec("mate-g2", "Queen and king team up", "Mate in one", "8/8/8/8/8/5kq1/8/7K b - - 0 1", "g3g2", "Place the queen next to the king, with your king guarding it."),
    PuzzleSpec("mate-b2", "The supported queen", "Mate in one", "8/8/8/8/8/2k5/2q5/K7 b - - 0 1", "c2b2", "The queen can give check from a protected square."),
)

private fun puzzleOfTheDay(): PuzzleSpec {
    val index = Math.floorMod(java.time.LocalDate.now().toEpochDay(), puzzles.size.toLong()).toInt()
    return puzzles[index]
}

private data class LessonSpec(val id: String, val title: String, val body: String, val question: String, val answers: List<String>, val correct: Int)
private val lessons = listOf(
    LessonSpec("opening-basics", "Opening principles", "Develop pieces toward useful squares, contest the center, and castle before launching a pawn attack. Avoid moving the same piece several times without a reason.", "Which is a sound opening habit?", listOf("Develop a knight toward the center", "Move the rook before developing", "Bring the queen out on move one"), 0),
    LessonSpec("forks", "Knight forks", "A fork attacks two or more targets at once. Knights are especially good at forks because their attack cannot be blocked.", "What makes a knight fork difficult to stop?", listOf("The knight attacks in an L shape and can jump", "The knight moves only in straight lines", "The knight can move through occupied squares"), 0),
    LessonSpec("king-safety", "Keep your king safe", "Castling moves the king away from the center and brings a rook into the game. Clear the squares between king and rook, then castle before the position opens.", "What must be true before castling?", listOf("The king has not moved and does not cross an attacked square", "The queen has been captured", "The king is currently in check"), 0),
)

@Composable
private fun GameSetupPage(
    initialMode: GameMode,
    initialBotStyle: BotStyle,
    onBack: () -> Unit,
    onStart: (GameMode, Int, BotStyle, Side) -> Unit
) {
    var mode by remember(initialMode) { mutableStateOf(initialMode) }
    var minutes by remember { mutableIntStateOf(10) }
    var botStyle by remember(initialBotStyle) { mutableStateOf(initialBotStyle) }
    var chosenSide by remember { mutableStateOf<Side?>(Side.WHITE) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("‹  Play", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Start a game", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Choose an opponent and a time control.", color = Muted)
        SetupChoice("Play computer", "An offline opponent on this device", mode == GameMode.COMPUTER) { mode = GameMode.COMPUTER }
        SetupChoice("Two players", "Take turns on the same device", mode == GameMode.LOCAL) { mode = GameMode.LOCAL }
        Text("Time control", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1, 3, 5, 10, 15).forEach { value ->
                Button(onClick = { minutes = value }, colors = ButtonDefaults.buttonColors(
                    containerColor = if (minutes == value) Green else PanelRaised,
                    contentColor = if (minutes == value) Ink else Cream
                )) { Text(if (value == 1) "1 min" else "$value min") }
            }
        }
        if (mode == GameMode.COMPUTER) {
            Text("Play as", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Side.WHITE to "White", Side.BLACK to "Black").forEach { (side, label) ->
                    Button(onClick = { chosenSide = side }, colors = ButtonDefaults.buttonColors(
                        containerColor = if (chosenSide == side) Green else PanelRaised,
                        contentColor = if (chosenSide == side) Ink else Cream
                    )) { Text(label) }
                }
                Button(onClick = { chosenSide = null }, colors = ButtonDefaults.buttonColors(
                    containerColor = if (chosenSide == null) Green else PanelRaised,
                    contentColor = if (chosenSide == null) Ink else Cream
                )) { Text("Random") }
            }
            Text("Choose your bot", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            BotStyle.entries.forEach { profile ->
                SetupChoice("${profile.displayName} · ${profile.rating}", profile.description, botStyle == profile) { botStyle = profile }
            }
        }
        Button(onClick = { onStart(mode, minutes, botStyle, chosenSide ?: if (kotlin.random.Random.nextBoolean()) Side.WHITE else Side.BLACK) }, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Green), shape = RoundedCornerShape(12.dp)) {
            Text("Start ${if (mode == GameMode.COMPUTER) "computer" else "local"} game", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SetupChoice(title: String, description: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(if (selected) GreenDark else Panel, RoundedCornerShape(12.dp))
        .clickable(onClick = onClick).padding(16.dp)) {
        Text(title, color = Cream, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(description, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun PuzzleHub(store: ProgressStore, onBack: () -> Unit, onPuzzle: (String, Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  Home", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Puzzles", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Puzzle rating  ${store.puzzleRating()}   ·   ${puzzles.count { store.isPuzzleSolved(it.id) }} solved", color = Muted)
        val daily = puzzleOfTheDay()
        SetupChoice("Daily puzzle · ${daily.title}", if (store.hasSolvedDailyPuzzleToday()) "Solved today · ${store.dailyPuzzleStreak()} day streak" else "One challenge today · ${store.dailyPuzzleStreak()} day streak", store.hasSolvedDailyPuzzleToday()) {
            onPuzzle(daily.id, true)
        }
        puzzles.forEach { puzzle ->
            SetupChoice("${puzzle.title}  ·  ${puzzle.level}", if (store.isPuzzleSolved(puzzle.id)) "Solved · replay" else "Solve puzzle", false) {
                onPuzzle(puzzle.id, false)
            }
        }
        Text("More daily puzzle packs will be added as original Chessifyy content.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun PuzzleScreen(puzzleId: String, daily: Boolean, store: ProgressStore, onBack: () -> Unit) {
    val puzzle = puzzles.firstOrNull { it.id == puzzleId } ?: puzzles.first()
    val game = remember(puzzle.id, daily) { ChessGame().apply { loadFen(puzzle.fen) } }
    var revision by remember(puzzle.id, daily) { mutableIntStateOf(0) }
    var selected by remember(puzzle.id, daily) { mutableStateOf<Int?>(null) }
    var feedback by remember(puzzle.id, daily) { mutableStateOf("Find the strongest move. Tap a piece to see its legal squares.") }
    var solved by remember(puzzle.id, daily) { mutableStateOf(if (daily) store.hasSolvedDailyPuzzleToday() else store.isPuzzleSolved(puzzle.id)) }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("‹  Puzzles", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text(puzzle.title, color = Cream, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(if (solved) "Solved${if (daily) " · ${store.dailyPuzzleStreak()} day streak" else ""}" else "${if (daily) "Daily · " else ""}${puzzle.level} · ${game.sideToMove.displayName()} to move", color = if (solved) Green else Muted)
        key(revision) {
            ChessBoard(game, selected, false) { square ->
                if (!solved) {
                    val choices = selected?.let { from -> game.legalMovesFrom(from).filter { it.to == square } }.orEmpty()
                    if (choices.isNotEmpty()) {
                        val move = choices.first()
                        val uci = squareToUci(move.from) + squareToUci(move.to) + (move.promotion?.san?.lowercase() ?: "")
                        if (uci == puzzle.solution) {
                            game.makeMove(move)
                            store.markPuzzleSolved(puzzle.id)
                            if (daily) store.recordDailyPuzzleSolved()
                            solved = true
                            feedback = "Excellent — ${game.status.message}"
                        } else {
                            game.undo()
                            feedback = "Not the puzzle solution. Try a different move."
                        }
                        selected = null
                        revision++
                    } else selected = if (game.pieces[square]?.side == game.sideToMove) square else null
                }
            }
        }
        Text(feedback, color = if (solved) Green else Cream)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { feedback = puzzle.hint }) { Text("Hint") }
            TextButton(onClick = { selected = null; feedback = puzzle.hint }) { Text("Clear selection") }
            if (solved) TextButton(onClick = {
                game.loadFen(puzzle.fen)
                solved = false
                selected = null
                feedback = "Find the strongest move. Tap a piece to see its legal squares."
                revision++
            }) { Text("Try again") }
        }
    }
}

private fun squareToUci(square: Int) = "${('a'.code + square % 8).toChar()}${8 - square / 8}"
private fun formatMove(move: ChessMove) = squareToUci(move.from) + squareToUci(move.to) + (move.promotion?.san?.lowercase() ?: "")
private fun formatEvaluation(centipawns: Int) = "Eval ${"%+.2f".format(centipawns / 100.0)} for White"

private val openingBookLines = listOf(
    listOf("e2e4", "e7e5", "g1f3", "b8c6", "f1b5", "a7a6", "b5a4", "g8f6"),
    listOf("e2e4", "c7c5", "g1f3", "d7d6", "d2d4", "c5d4", "f3d4", "g8f6"),
    listOf("d2d4", "d7d5", "c2c4", "e7e6", "b1c3", "g8f6", "c1g5"),
    listOf("d2d4", "g8f6", "c1f4", "e7e6", "e2e3", "d7d5", "g1f3")
)

private fun isBookMoveThrough(moves: List<String>): Boolean =
    moves.isNotEmpty() && openingBookLines.any { line -> moves.size <= line.size && line.take(moves.size) == moves }

@Composable
private fun LessonHub(store: ProgressStore, onBack: () -> Unit, onLesson: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  Home", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Learn", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Short lessons and practical checks. All content is free.", color = Muted)
        lessons.forEach { lesson ->
            SetupChoice(lesson.title, if (store.isLessonComplete(lesson.id)) "Completed · review" else "Lesson · 2 min", selected = store.isLessonComplete(lesson.id)) {
                onLesson(lesson.id)
            }
        }
    }
}

@Composable
private fun LessonScreen(lessonId: String, store: ProgressStore, onBack: () -> Unit) {
    val lesson = lessons.firstOrNull { it.id == lessonId } ?: lessons.first()
    var answer by remember(lesson.id) { mutableIntStateOf(-1) }
    var complete by remember(lesson.id) { mutableStateOf(store.isLessonComplete(lesson.id)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("‹  Lessons", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text(lesson.title, color = Cream, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(lesson.body, color = Cream, fontSize = 16.sp, lineHeight = 24.sp)
        Text(lesson.question, color = Green, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        lesson.answers.forEachIndexed { index, choice ->
                SetupChoice(choice, if (answer == index) if (index == lesson.correct) "Correct" else "Try again" else "", selected = answer == index) {
                answer = index
                if (index == lesson.correct) {
                    store.completeLesson(lesson.id)
                    complete = true
                }
            }
        }
        if (complete) Text("Lesson complete", color = Green, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MoreHub(onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("More", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Your profile, community, and game tools", color = Muted)
        listOf("Ratings", "Analysis", "Game Review", "Tournaments", "Clubs", "Chat", "Online").forEach { destination ->
            SetupChoice(destination, when (destination) {
                "Analysis" -> "Review positions with the local chess engine"
                "Game Review" -> "Replay your last game and inspect move quality"
                "Clubs", "Chat", "Online", "Tournaments" -> "Online service setup is required for cross-device play"
                else -> "Your Chessifyy progress"
            }, false) { onOpen(destination) }
        }
    }
}

@Composable
private fun GameReviewPage(recentGames: List<GameReviewData>, onBack: () -> Unit, onNewGame: () -> Unit) {
    var selectedGameIndex by remember(recentGames) { mutableIntStateOf(0) }
    val review = recentGames.getOrNull(selectedGameIndex)
    var moveReviews by remember(review) { mutableStateOf<List<MoveReview>?>(null) }
    var selectedPly by remember(review) { mutableIntStateOf(0) }
    LaunchedEffect(review) {
        moveReviews = review?.let { saved ->
            withContext(Dispatchers.Default) {
                saved.movesUci.indices.take(120).mapNotNull { index ->
                    val position = ChessGame()
                    if (!position.loadFen(saved.positions[index])) null
                    else position.reviewMove(
                        saved.movesUci[index], saved.movesSan[index], index + 1, depth = 2,
                        openingBookMove = isBookMoveThrough(saved.movesUci.take(index + 1))
                    )
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("‹  Back", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Game review", color = Cream, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        if (recentGames.isNotEmpty()) {
            Text("Recent games", color = Green, fontWeight = FontWeight.SemiBold)
            recentGames.forEachIndexed { index, saved ->
                SetupChoice(
                    "Game ${recentGames.size - index} · ${android.text.format.DateFormat.format("MMM d, h:mm a", saved.savedAt)}",
                    "${saved.movesSan.size} moves · ${saved.result}",
                    selected = index == selectedGameIndex
                ) { selectedGameIndex = index }
            }
        }
        if (review == null) {
            Text("Finish a game to create a review. Up to 20 recent games are saved on this device.", color = Muted)
            return@Column
        }
        Text(review.result, color = Green, fontWeight = FontWeight.SemiBold)
        val reviewedMoves = moveReviews
        when {
            reviewedMoves == null -> Text("Reviewing ${review.movesUci.size} moves with the local engine…", color = Muted)
            reviewedMoves.isEmpty() -> Text("This game has no moves to review.", color = Muted)
            else -> {
                val focusMoves = if (review.humanSide == null) reviewedMoves else reviewedMoves.filter { it.side == review.humanSide }
                val averageLoss = focusMoves.map { it.centipawnLoss }.average().takeIf { it.isFinite() } ?: 0.0
                val accuracy = if (focusMoves.isEmpty()) null else (100.0 * Math.exp(-averageLoss / 250.0)).toInt().coerceIn(0, 100)
                Text(accuracy?.let { "Approx. accuracy ${it}% · ${if (review.humanSide == null) "both players" else review.humanSide.displayName()} · ${focusMoves.size} moves" }
                    ?: "No player moves are available for an accuracy estimate.", color = Cream, fontWeight = FontWeight.SemiBold)
                accuracy?.takeIf { review.opponentRating != null }?.let { value ->
                    Text("Estimated Chess.com-style game performance", color = Green, fontWeight = FontWeight.SemiBold)
                    Text(estimateGamePerformance(review, value).toString(), color = Cream, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("A single-game estimate from move quality and bot strength; not an official Chess.com rating or your account rating.", color = Muted, fontSize = 12.sp)
                }
                if (accuracy != null && review.opponentRating == null) {
                    Text("A bot-based performance estimate is available after computer games.", color = Muted, fontSize = 12.sp)
                }
                Text("Estimated from a shallow on-device search; use it as a practice guide, not an official rating.", color = Muted, fontSize = 12.sp)
                val qualitySummary = reviewedMoves.groupingBy { it.quality }.eachCount()
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Brilliant", "Great", "Best", "Excellent", "Good", "Book Move", "Inaccuracy", "Mistake", "Blunder").forEach { label ->
                        qualitySummary[label]?.let { count -> Text("$label $count", color = qualityTint(label), fontSize = 12.sp) }
                    }
                }

                val maxPosition = review.positions.lastIndex
                val step = selectedPly.coerceIn(0, maxPosition)
                val board = remember(review, step) { ChessGame().apply { loadFen(review.positions[step]) } }
                ChessBoard(board, selectedSquare = null, flipped = false, onTap = {})
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectedPly = (step - 1).coerceAtLeast(0) }, enabled = step > 0) { Text("‹ Previous") }
                    Text(if (step == 0) "Starting position" else "After move $step", color = Cream)
                    TextButton(onClick = { selectedPly = (step + 1).coerceAtMost(maxPosition) }, enabled = step < maxPosition) { Text("Next ›") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selectedPly = 0 }) { Text("Start", color = if (step == 0) Green else Cream) }
                    review.movesSan.forEachIndexed { index, san ->
                        TextButton(onClick = { selectedPly = index + 1 }) {
                            Text("${index / 2 + 1}${if (index % 2 == 0) "." else "…"} $san", color = if (step == index + 1) Green else Cream)
                        }
                    }
                }
                val selectedReview = reviewedMoves.getOrNull(step - 1)
                if (selectedReview != null) {
                    val tint = qualityTint(selectedReview.quality)
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("${selectedReview.side.displayName()} played ${selectedReview.playedSan} · ${selectedReview.quality}", color = tint, fontWeight = FontWeight.Bold)
                        Text(qualityDescription(selectedReview.quality), color = Muted, fontSize = 12.sp)
                        Text("Local best move: ${selectedReview.bestUci} · estimated loss ${selectedReview.centipawnLoss} cp", color = Cream, fontSize = 13.sp)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onNewGame, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Play again", color = Ink) }
                    TextButton(onClick = onBack) { Text("Done") }
                }
            }
        }
    }
}

private fun qualityTint(quality: String) = when (quality) {
    "Brilliant" -> Color(0xFF64D4E8)
    "Great" -> Color(0xFF78A9FF)
    "Best" -> Green
    "Excellent" -> Color(0xFFB5D879)
    "Good" -> Color(0xFFB3C9A0)
    "Book Move" -> Color(0xFFD5B27B)
    "Inaccuracy" -> Color(0xFFE6C85C)
    else -> Color(0xFFE28C71)
}

private fun qualityDescription(quality: String) = when (quality) {
    "Brilliant" -> "A strong material sacrifice that the local search still approves."
    "Great" -> "A critical move that preserves a large advantage in this search."
    "Best" -> "Matches the top move found by Chessifyy's local search."
    "Excellent" -> "Very close to the top local engine choice."
    "Good" -> "A sound move with a small estimated cost."
    "Book Move" -> "Matches one of Chessifyy's short built-in opening lines."
    "Inaccuracy" -> "Gives away some of the position's value."
    "Mistake" -> "Loses a meaningful amount of the position's value."
    else -> "A large evaluation drop in the local search."
}

private fun estimateGamePerformance(review: GameReviewData, accuracy: Int): Int {
    val opponent = review.opponentRating
    val resultScore = review.score
    if (opponent != null && resultScore != null) {
        val qualityScore = 1.0 / (1.0 + Math.pow(10.0, -(accuracy - 70) / 18.0))
        val blendedScore = (qualityScore * 0.65 + resultScore * 0.35).coerceIn(0.05, 0.95)
        return (opponent + 400.0 * (kotlin.math.ln(blendedScore / (1.0 - blendedScore)) / kotlin.math.ln(10.0)))
            .toInt().coerceIn(100, 3000)
    }
    return (review.baselineRating + (accuracy - 75) * 14).coerceIn(100, 3000)
}

@Composable
private fun RatingsPage(store: ProgressStore, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  More", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Your ratings", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        listOf("Rapid" to store.rapidRating(), "Blitz" to store.blitzRating(), "Bullet" to store.bulletRating(), "Puzzles" to store.puzzleRating()).forEach { (label, rating) ->
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, color = Cream, fontSize = 17.sp)
                Text(rating.toString(), color = Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("Estimated playing strength", color = Green, fontWeight = FontWeight.SemiBold)
        listOf("Rapid" to store.estimatedElo(10), "Blitz" to store.estimatedElo(3), "Bullet" to store.estimatedElo(1)).forEach { (label, estimate) ->
            Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(label, color = Cream, fontWeight = FontWeight.SemiBold)
                    Text(estimate?.let { "Provisional estimate · ${it.games} bot games" } ?: "Play computer games to get an estimate", color = Muted, fontSize = 12.sp)
                }
                Text(estimate?.rating?.toString() ?: "—", color = Green, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("Computer game record", color = Green, fontWeight = FontWeight.SemiBold)
        Text("${store.gamesWon()} wins   ·   ${store.gamesDrawn()} draws   ·   ${store.gamesLost()} losses   ·   ${store.gamesPlayed()} games", color = Cream)
        Text("Running ratings use provisional Chessifyy bot baselines (Pip 700, Tess 950, Sage 1250, Orion 1650). Performance estimates use your score with a small-sample adjustment; both are approximate until calibrated against real players. Online ratings need a hosted service.", color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun AnalysisPage(onBack: () -> Unit) {
    val game = remember { ChessGame() }
    var selected by remember { mutableStateOf<Int?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var thinking by remember { mutableStateOf(false) }
    var analysisDepth by remember { mutableIntStateOf(3) }
    var candidates by remember { mutableStateOf<List<AnalysisCandidate>>(emptyList()) }
    var fenDialog by remember { mutableStateOf(false) }
    var fenDraft by remember { mutableStateOf("") }
    var fenError by remember { mutableStateOf(false) }
    var analysis by remember { mutableStateOf("Select pieces to explore a position, then request a candidate move.") }
    LaunchedEffect(thinking) {
        if (thinking) {
            val fen = game.toFen()
            val report = withContext(Dispatchers.Default) {
                val position = ChessGame()
                if (!position.loadFen(fen)) null else position.sideToMove to position.analyzeCandidates(analysisDepth, 3)
            }
            if (fen == game.toFen()) {
                candidates = report?.second.orEmpty()
                analysis = when {
                    report == null -> "This position could not be analyzed."
                    report.second.isEmpty() -> "No legal moves in this position."
                    else -> "Search complete · depth ${analysisDepth} ply · top ${report.second.size} candidate moves"
                }
            }
            thinking = false
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("‹  More", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Analysis board", color = Cream, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("On-device search · evaluation is an estimate, not a cloud engine score", color = Muted, fontSize = 12.sp)
        key(revision) {
            ChessBoard(game, selected, false) { square ->
                if (!thinking) {
                val moves = selected?.let { from -> game.legalMovesFrom(from).filter { it.to == square } }.orEmpty()
                if (moves.isNotEmpty()) {
                    game.makeMove(moves.first()); selected = null; candidates = emptyList(); revision++; analysis = "Position changed · analyze to refresh candidate lines."
                } else selected = if (game.pieces[square]?.side == game.sideToMove) square else null
                }
            }
        }
        Text(analysis, color = Cream, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { game.undo(); selected = null; candidates = emptyList(); revision++; analysis = "Move undone · analyze to refresh candidate lines." }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) { Text("Undo", color = Cream) }
            Button(onClick = { game.reset(); selected = null; candidates = emptyList(); revision++; analysis = "Starting position restored · analyze to refresh candidate lines." }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) { Text("Reset", color = Cream) }
            Button(onClick = { fenDraft = game.toFen(); fenError = false; fenDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = PanelRaised)) { Text("Load FEN", color = Cream) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Depth", color = Muted, fontSize = 12.sp)
            listOf(1, 2, 3).forEach { depth ->
                TextButton(onClick = { analysisDepth = depth }, enabled = !thinking) {
                    Text("$depth", color = if (analysisDepth == depth) Green else Cream)
                }
            }
            Button(enabled = !thinking, onClick = {
                candidates = emptyList()
                thinking = true
                analysis = "Searching…"
            }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text(if (thinking) "Searching" else "Analyze", color = Ink) }
        }
        candidates.forEachIndexed { index, candidate ->
            val rootSide = game.sideToMove
            val whiteCp = if (rootSide == Side.WHITE) candidate.scoreCp else -candidate.scoreCp
            val expectedScore = (100.0 / (1.0 + Math.pow(10.0, -whiteCp / 400.0))).toInt()
            Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(10.dp)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${index + 1}. ${formatMove(candidate.move)}   ${formatEvaluation(whiteCp)}", color = if (index == 0) Green else Cream, fontWeight = FontWeight.SemiBold)
                Text("Line: ${candidate.line.joinToString("  ") { formatMove(it) }}", color = Cream, fontSize = 13.sp)
                Text("Approx. White expected score: $expectedScore%", color = Muted, fontSize = 12.sp)
            }
        }
    }
    if (fenDialog) {
        AlertDialog(
            onDismissRequest = { fenDialog = false },
            title = { Text("Load a position") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Paste a standard chess FEN position.", color = Muted)
                    OutlinedTextField(value = fenDraft, onValueChange = { fenDraft = it; fenError = false }, label = { Text("FEN") }, minLines = 2)
                    if (fenError) Text("That FEN is invalid. The current board was kept.", color = Color(0xFFF2A08C))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (game.loadFen(fenDraft)) {
                        selected = null
                        candidates = emptyList()
                        revision++
                        analysis = "Position loaded. Request a candidate move to analyze it."
                        fenDialog = false
                    } else fenError = true
                }) { Text("Load") }
            },
            dismissButton = { TextButton(onClick = { fenDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TournamentPage(store: ProgressStore, onBack: () -> Unit, onPlay: () -> Unit) {
    var revision by remember { mutableIntStateOf(0) }
    val joined = remember(revision) { store.tournamentJoined() }
    val roundsPlayed = remember(revision) { store.tournamentRoundsPlayed() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  More", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Tournaments", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Free Chessifyy practice event · three 10 minute games against the computer", color = Muted)
        SetupChoice("Chessifyy Rapid Series", "10+0 · 3 rounds · saved on this device", joined) {
            store.joinTournament()
            revision++
        }
        if (joined) {
            Text(if (roundsPlayed < 3) "Round ${roundsPlayed + 1} of 3" else "Series complete", color = Green, fontWeight = FontWeight.Bold)
            Text("Score: ${store.tournamentPoints()} / 3 points", color = Cream)
            if (roundsPlayed < 3) {
                Button(onClick = onPlay, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                    Text("Play round ${roundsPlayed + 1}", color = Ink)
                }
            } else TextButton(onClick = { store.resetTournament(); revision++ }) { Text("Start a new series") }
        }
        Text("Online tournaments with other players require a hosted Chessifyy service.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ClubsPage(store: ProgressStore, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  More", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Clubs", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Create local club spaces. Online memberships need the service endpoint.", color = Muted)
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Club name") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { if (store.createClub(name)) { name = ""; revision++ } }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Create club", color = Ink) }
        Text("On this device", color = Green, fontWeight = FontWeight.SemiBold)
        key(revision) { store.clubs().forEach { SetupChoice(it, "Private local space", false) {} } }
    }
}

@Composable
private fun ChatPage(store: ProgressStore, onBack: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    var revision by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("‹  More", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Chat", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Local notes on this device. Live club and friend chat require the online service.", color = Muted, fontSize = 12.sp)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            key(revision) { store.chatMessages().forEach { message -> Text(message, Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(12.dp), color = Cream) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = draft, onValueChange = { draft = it }, label = { Text("Write a note") }, modifier = Modifier.weight(1f))
            TextButton(onClick = { if (draft.isNotBlank()) { store.addLocalMessage("You: ${draft.trim()}"); draft = ""; revision++ } }) { Text("Send") }
        }
    }
}

@Composable
private fun OnlinePlayPage(onBack: () -> Unit) {
    var server by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("‹  Play", color = Muted, modifier = Modifier.clickable(onClick = onBack))
        Text("Online play", color = Cream, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("Matchmaking, accounts, clubs, live tournaments, and synced chat require a Chessifyy server.", color = Muted)
        OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("Server address") }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://your-chessifyy-server") })
        Text("A server client and deployable backend are the next online milestone. No game or messages are sent from this screen yet.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ChessBoard(
    game: ChessGame,
    selectedSquare: Int?,
    flipped: Boolean,
    onDrop: (Int, Int) -> Unit = { _, _ -> },
    onTap: (Int) -> Unit
) {
    val pieces = game.pieces
    val legalTargets = selectedSquare?.let { game.legalMovesFrom(it) }.orEmpty()
    val checkedKing = when {
        game.whiteInCheck -> pieces.indexOfFirst { it == ChessPiece(Side.WHITE, ChessPieceType.KING) }
        game.blackInCheck -> pieces.indexOfFirst { it == ChessPiece(Side.BLACK, ChessPieceType.KING) }
        else -> -1
    }
    val lastMove = game.lastMove
    val displayedSquares = if (flipped) (63 downTo 0).toList() else (0..63).toList()

    Column(
        Modifier.fillMaxWidth().aspectRatio(1f)
            .pointerInput(flipped, game.uciHistory) {
                val inset = 5.dp.toPx()
                fun squareAt(point: androidx.compose.ui.geometry.Offset): Int? {
                    val boardWidth = size.width - inset * 2
                    val boardHeight = size.height - inset * 2
                    val x = point.x - inset
                    val y = point.y - inset
                    if (x < 0f || y < 0f || x >= boardWidth || y >= boardHeight) return null
                    val col = (x / boardWidth * 8).toInt().coerceIn(0, 7)
                    val row = (y / boardHeight * 8).toInt().coerceIn(0, 7)
                    val visualIndex = row * 8 + col
                    return if (flipped) 63 - visualIndex else visualIndex
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPosition = down.position
                    var endPosition = downPosition
                    var dragging = false
                    val slopSquared = viewConfiguration.touchSlop * viewConfiguration.touchSlop
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        endPosition = change.position
                        val dx = endPosition.x - downPosition.x
                        val dy = endPosition.y - downPosition.y
                        if (!dragging && dx * dx + dy * dy > slopSquared) dragging = true
                        if (dragging) change.consume()
                    } while (change.pressed)
                    val from = squareAt(downPosition)
                    val to = squareAt(endPosition)
                    if (dragging && from != null && to != null && from != to) onDrop(from, to)
                    else if (!dragging && from != null) onTap(from)
                }
            }
            .background(Panel, RoundedCornerShape(10.dp)).padding(5.dp)
    ) {
        displayedSquares.chunked(8).forEach { row ->
            Row(Modifier.weight(1f)) {
                row.forEach { square ->
                    val piece = pieces[square]
                    val isSelected = square == selectedSquare
                    val isLastMove = lastMove?.from == square || lastMove?.to == square
                    val isLegalTarget = legalTargets.any { it.to == square }
                    val base = if ((square / 8 + square % 8) % 2 == 0) Color(0xFFD9D5C7) else Color(0xFF76905B)
                    val color = when {
                        square == checkedKing -> Color(0xFFE27D69)
                        isSelected -> Color(0xFFB6C85B)
                        isLastMove -> Color(0xFFC2C75D)
                        else -> base
                    }
                    Box(
                        Modifier.weight(1f).fillMaxSize().background(color),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLegalTarget && piece == null) {
                            Box(Modifier.size(12.dp).background(Color(0x55000000), CircleShape))
                        }
                        if (isLegalTarget && piece != null) {
                            Box(Modifier.fillMaxSize().padding(4.dp).border(3.dp, Green, CircleShape))
                        }
                        if (piece != null) {
                            Text(
                                if (piece.side == Side.WHITE) piece.type.glyphWhite else piece.type.glyphBlack,
                                color = if (piece.side == Side.WHITE) Color(0xFFF8F5EB) else Color(0xFF22241F),
                                fontSize = 35.sp,
                                lineHeight = 38.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
