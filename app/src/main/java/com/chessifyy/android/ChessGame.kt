package com.chessifyy.android

enum class Side { WHITE, BLACK;
    fun other() = if (this == WHITE) BLACK else WHITE
}
fun Side.displayName() = if (this == Side.WHITE) "White" else "Black"

enum class ChessPieceType(val san: String, val glyphWhite: String, val glyphBlack: String) {
    KING("K", "♔", "♚"), QUEEN("Q", "♕", "♛"), ROOK("R", "♖", "♜"),
    BISHOP("B", "♗", "♝"), KNIGHT("N", "♘", "♞"), PAWN("", "♙", "♟")
}

enum class BotTemperament { BEGINNER, TACTICAL, POSITIONAL, BALANCED }
enum class BotStyle(
    val displayName: String,
    val rating: Int,
    val searchDepth: Int,
    val temperament: BotTemperament,
    val varietyCp: Int,
    val description: String
) {
    PIP("Pip", 700, 1, BotTemperament.BEGINNER, 240, "Curious beginner · takes chances and explores"),
    TESS("Tess", 950, 2, BotTemperament.TACTICAL, 90, "Tactical attacker · looks for checks and captures"),
    SAGE("Sage", 1250, 2, BotTemperament.POSITIONAL, 35, "Positional planner · values development and the center"),
    ORION("Orion", 1650, 3, BotTemperament.BALANCED, 0, "Strong all-rounder · searches deeper and plays steadily")
}

data class ChessPiece(val side: Side, val type: ChessPieceType)
data class ChessMove(val from: Int, val to: Int, val promotion: ChessPieceType? = null)
data class AnalysisCandidate(val move: ChessMove, val scoreCp: Int, val line: List<ChessMove>)
data class GameReviewData(
    val positions: List<String>,
    val movesUci: List<String>,
    val movesSan: List<String>,
    val result: String,
    val humanSide: Side?,
    val savedAt: Long = System.currentTimeMillis(),
    val baselineRating: Int = 1200,
    val opponentRating: Int? = null,
    val timeControlMinutes: Int = 10,
    val score: Double? = null
)
data class MoveReview(
    val ply: Int,
    val side: Side,
    val playedSan: String,
    val playedUci: String,
    val bestUci: String,
    val centipawnLoss: Int,
    val quality: String
)
data class GamePosition(val pieces: List<ChessPiece?>, val sideToMove: Side, val inCheck: Boolean)
data class GameStatus(val finished: Boolean, val result: String, val message: String, val winner: Side? = null)

/** Local standard-chess rules and game state. Square 0 is a8; square 63 is h1. */
class ChessGame {
    private var board = MutableList<ChessPiece?>(64) { null }
    private var castleRights = mutableSetOf("K", "Q", "k", "q")
    private var enPassant: Int? = null
    private var halfmoveClock = 0
    private var fullmoveNumber = 1
    private var repetition = mutableMapOf<String, Int>()
    private val undoStack = mutableListOf<Snapshot>()

    var sideToMove: Side = Side.WHITE
        private set
    var moveHistory: List<String> = emptyList()
        private set
    var positionHistory: List<String> = emptyList()
        private set
    var uciHistory: List<String> = emptyList()
        private set
    var status: GameStatus = GameStatus(false, "", "White to move")
        private set
    var lastMove: ChessMove? = null
        private set

    val pieces: List<ChessPiece?> get() = board.toList()
    val whiteInCheck: Boolean get() = isInCheck(Side.WHITE)
    val blackInCheck: Boolean get() = isInCheck(Side.BLACK)

    init { reset() }

    fun reset() {
        board = MutableList(64) { null }
        val backRank = listOf(
            ChessPieceType.ROOK, ChessPieceType.KNIGHT, ChessPieceType.BISHOP, ChessPieceType.QUEEN,
            ChessPieceType.KING, ChessPieceType.BISHOP, ChessPieceType.KNIGHT, ChessPieceType.ROOK
        )
        for (file in 0..7) {
            board[file] = ChessPiece(Side.BLACK, backRank[file])
            board[8 + file] = ChessPiece(Side.BLACK, ChessPieceType.PAWN)
            board[48 + file] = ChessPiece(Side.WHITE, ChessPieceType.PAWN)
            board[56 + file] = ChessPiece(Side.WHITE, backRank[file])
        }
        castleRights = mutableSetOf("K", "Q", "k", "q")
        enPassant = null
        halfmoveClock = 0
        fullmoveNumber = 1
        repetition = mutableMapOf()
        undoStack.clear()
        sideToMove = Side.WHITE
        moveHistory = emptyList()
        uciHistory = emptyList()
        lastMove = null
        status = GameStatus(false, "", "White to move")
        repetition[positionKey()] = 1
        positionHistory = listOf(toFen())
    }

    fun legalMovesFrom(square: Int): List<ChessMove> = legalMoves().filter { it.from == square }

    fun legalMoves(): List<ChessMove> {
        if (status.finished) return emptyList()
        return pseudoMoves(board, sideToMove).filter { move ->
            val moving = board[move.from] ?: return@filter false
            val next = board.toMutableList()
            applyTo(next, move, enPassant, moving.side)
            val king = next.indexOfFirst { it == ChessPiece(moving.side, ChessPieceType.KING) }
            king >= 0 && !isSquareAttacked(next, king, moving.side.other())
        }
    }

    fun makeMove(requested: ChessMove): Boolean {
        if (status.finished) return false
        val moving = board.getOrNull(requested.from) ?: return false
        if (moving.side != sideToMove) return false
        val promotion = requested.promotion ?: if (moving.type == ChessPieceType.PAWN &&
            (requested.to / 8 == 0 || requested.to / 8 == 7)) ChessPieceType.QUEEN else null
        val move = ChessMove(requested.from, requested.to, promotion)
        if (move !in legalMoves()) return false

        val notation = notationFor(move, moving)
        val uci = moveToUci(move)
        undoStack += snapshot()
        applyMove(move)
        lastMove = move
        sideToMove = sideToMove.other()
        if (sideToMove == Side.WHITE) fullmoveNumber++
        val key = positionKey()
        repetition[key] = (repetition[key] ?: 0) + 1
        status = calculateStatus()
        moveHistory = moveHistory + (notation + when {
            status.result == "checkmate" -> "#"
            isInCheck(sideToMove) -> "+"
            else -> ""
        })
        uciHistory = uciHistory + uci
        positionHistory = positionHistory + toFen()
        return true
    }

    fun undo(): Boolean {
        val previous = undoStack.removeLastOrNull() ?: return false
        board = previous.board.toMutableList()
        castleRights = previous.castleRights.toMutableSet()
        enPassant = previous.enPassant
        halfmoveClock = previous.halfmoveClock
        fullmoveNumber = previous.fullmoveNumber
        sideToMove = previous.sideToMove
        moveHistory = previous.moveHistory
        positionHistory = previous.positionHistory
        uciHistory = previous.uciHistory
        repetition = previous.repetition.toMutableMap()
        lastMove = previous.lastMove
        status = previous.status
        return true
    }

    fun resign(side: Side = sideToMove) {
        if (!status.finished) status = GameStatus(true, "resignation", "${side.other().label()} wins — ${side.label()} resigned", side.other())
    }

    fun agreeDraw() {
        if (!status.finished) status = GameStatus(true, "draw", "Draw agreed")
    }

    fun flagFall(side: Side) {
        if (status.finished) return
        val opponentCanMate = board.any { it?.side == side.other() && it.type in setOf(ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.PAWN) } ||
            board.count { it?.side == side.other() && it.type in setOf(ChessPieceType.BISHOP, ChessPieceType.KNIGHT) } >= 2
        status = if (opponentCanMate) {
            GameStatus(true, "timeout", "${side.other().label()} wins on time", side.other())
        } else GameStatus(true, "timeout-draw", "Draw — opponent has insufficient mating material")
    }

    fun position(): GamePosition = GamePosition(pieces, sideToMove, isInCheck(sideToMove))
    fun materialEvaluation(side: Side = sideToMove): Int = materialScore(side)

    fun toFen(): String {
        val placement = (0..7).joinToString("/") { row ->
            var empty = 0
            buildString {
                for (file in 0..7) {
                    val piece = board[row * 8 + file]
                    if (piece == null) empty++ else {
                        if (empty > 0) { append(empty); empty = 0 }
                        val letter = when (piece.type) {
                            ChessPieceType.KING -> "k"; ChessPieceType.QUEEN -> "q"; ChessPieceType.ROOK -> "r"
                            ChessPieceType.BISHOP -> "b"; ChessPieceType.KNIGHT -> "n"; ChessPieceType.PAWN -> "p"
                        }
                        append(if (piece.side == Side.WHITE) letter.uppercase() else letter)
                    }
                }
                if (empty > 0) append(empty)
            }
        }
        val side = if (sideToMove == Side.WHITE) "w" else "b"
        val rights = listOf("K", "Q", "k", "q").filter { it in castleRights }.joinToString("").ifEmpty { "-" }
        val ep = enPassant?.let { squareName(it) } ?: "-"
        return "$placement $side $rights $ep $halfmoveClock $fullmoveNumber"
    }

    fun loadFen(fen: String): Boolean {
        val fields = fen.trim().split(Regex("\\s+"))
        if (fields.size !in 4..6 || fields[1] !in setOf("w", "b")) return false
        if (fields[2] != "-" && (fields[2].any { it !in "KQkq" } || fields[2].toSet().size != fields[2].length)) return false
        val parsedEnPassant = if (fields[3] == "-") null else parseSquare(fields[3]) ?: return false
        if (parsedEnPassant != null && parsedEnPassant / 8 !in setOf(2, 5)) return false
        val parsedHalfmove = fields.getOrNull(4)?.toIntOrNull()?.takeIf { it >= 0 } ?: if (fields.size >= 5) return false else 0
        val parsedFullmove = fields.getOrNull(5)?.toIntOrNull()?.takeIf { it > 0 } ?: if (fields.size >= 6) return false else 1
        val ranks = fields[0].split("/")
        if (ranks.size != 8) return false
        val parsed = MutableList<ChessPiece?>(64) { null }
        for ((row, rankText) in ranks.withIndex()) {
            var file = 0
            for (symbol in rankText) {
                if (symbol.isDigit()) {
                    val emptySquares = symbol.digitToInt()
                    if (emptySquares !in 1..8) return false
                    file += emptySquares
                }
                else {
                    if (file !in 0..7) return false
                    val type = when (symbol.lowercaseChar()) {
                        'k' -> ChessPieceType.KING; 'q' -> ChessPieceType.QUEEN; 'r' -> ChessPieceType.ROOK
                        'b' -> ChessPieceType.BISHOP; 'n' -> ChessPieceType.KNIGHT; 'p' -> ChessPieceType.PAWN
                        else -> return false
                    }
                    parsed[row * 8 + file] = ChessPiece(if (symbol.isUpperCase()) Side.WHITE else Side.BLACK, type)
                    file++
                }
            }
            if (file != 8) return false
        }
        if (parsed.count { it == ChessPiece(Side.WHITE, ChessPieceType.KING) } != 1 ||
            parsed.count { it == ChessPiece(Side.BLACK, ChessPieceType.KING) } != 1) return false
        // Validate the complete FEN before replacing the active position.
        board = parsed
        sideToMove = if (fields[1] == "b") Side.BLACK else Side.WHITE
        castleRights = if (fields[2] == "-") mutableSetOf() else fields[2].filter { it in "KQkq" }.map { it.toString() }.toMutableSet()
        enPassant = parsedEnPassant
        halfmoveClock = parsedHalfmove
        fullmoveNumber = parsedFullmove
        moveHistory = emptyList()
        uciHistory = emptyList()
        lastMove = null
        undoStack.clear()
        repetition = mutableMapOf()
        status = GameStatus(false, "", "${sideToMove.displayName()} to move")
        repetition[positionKey()] = 1
        status = calculateStatus()
        positionHistory = listOf(toFen())
        return true
    }

    /** Returns a material-search move for the current side. Depth 2–3 is suitable for mobile play. */
    fun findBestMove(depth: Int = 2, style: BotStyle = BotStyle.ORION): ChessMove? {
        val candidates = legalMoves()
        if (candidates.isEmpty()) return null
        val computer = sideToMove
        val scored = candidates.mapNotNull { move ->
            val child = copyForSearch()
            if (!child.makeMove(move)) null
            else move to child.searchScore((depth - 1).coerceAtLeast(0), computer, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1)
        }
        val bestScore = scored.maxOfOrNull { it.second } ?: return null
        val shortlist = scored.filter { bestScore - it.second <= style.varietyCp }
        return when (style.temperament) {
            BotTemperament.BEGINNER -> shortlist.random().first
            BotTemperament.TACTICAL -> shortlist.maxByOrNull { (move, _) ->
                val child = copyForSearch().also { it.makeMove(move) }
                moveOrderingScore(move) + if (child.isInCheck(child.sideToMove)) 90 else 0
            }?.first
            BotTemperament.POSITIONAL -> shortlist.maxByOrNull { (move, _) ->
                val piece = board[move.from]
                val center = 7 - (kotlin.math.abs(3 - move.to / 8) + kotlin.math.abs(3 - move.to % 8))
                center * if (piece?.type in setOf(ChessPieceType.KNIGHT, ChessPieceType.BISHOP, ChessPieceType.PAWN)) 5 else 1
            }?.first
            BotTemperament.BALANCED -> shortlist.maxByOrNull { it.second }?.first
        }
    }

    /** Scores each legal root move and returns the top alternatives with a short principal line. */
    fun analyzeCandidates(depth: Int = 3, limit: Int = 3): List<AnalysisCandidate> {
        val rootSide = sideToMove
        return legalMoves().mapNotNull { move ->
            val position = copyForSearch()
            if (!position.makeMove(move)) return@mapNotNull null
            val score = position.searchScore((depth - 1).coerceAtLeast(0), rootSide, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1)
            val line = mutableListOf(move)
            var cursor = position
            var remaining = depth - 1
            while (remaining > 0 && !cursor.status.finished) {
                val continuation = cursor.findBestMove(remaining) ?: break
                if (!cursor.makeMove(continuation)) break
                line += continuation
                remaining--
            }
            AnalysisCandidate(move, score, line)
        }.sortedByDescending { it.scoreCp }.take(limit.coerceAtLeast(1))
    }

    fun reviewMove(uci: String, san: String, ply: Int, depth: Int = 2, openingBookMove: Boolean = false): MoveReview? {
        val played = parseUci(uci) ?: return null
        val legal = legalMoves()
        if (played !in legal) return null
        val best = findBestMove(depth) ?: return null
        val player = sideToMove
        val beforeEvaluation = materialScore(player)
        fun score(move: ChessMove): Int {
            val child = copyForSearch()
            if (!child.makeMove(move)) return Int.MIN_VALUE / 2
            return child.searchScore((depth - 1).coerceAtLeast(0), player, Int.MIN_VALUE + 1, Int.MAX_VALUE - 1)
        }
        val bestScore = score(best)
        val playedScore = score(played)
        val loss = (bestScore - playedScore).coerceAtLeast(0)
        val movingPiece = board[played.from] ?: return null
        val captured = board[played.to]
        val materialValues = mapOf(ChessPieceType.PAWN to 100, ChessPieceType.KNIGHT to 320, ChessPieceType.BISHOP to 335,
            ChessPieceType.ROOK to 500, ChessPieceType.QUEEN to 900, ChessPieceType.KING to 20_000)
        val materialSacrifice = movingPiece.type !in setOf(ChessPieceType.PAWN, ChessPieceType.KING) &&
            materialValues.getValue(movingPiece.type) - (captured?.let { materialValues.getValue(it.type) } ?: 0) >= 250
        val afterPlayed = copyForSearch().also { it.makeMove(played) }
        val sacrificeWasAccepted = materialSacrifice && afterPlayed.legalMoves().any { it.to == played.to }
        val onlyStrongMove = bestScore >= beforeEvaluation + 220 && bestScore - playedScore <= 20
        val quality = when {
            openingBookMove -> "Book Move"
            sacrificeWasAccepted && loss <= 20 -> "Brilliant"
            onlyStrongMove -> "Great"
            loss <= 12 -> "Best"
            loss <= 40 -> "Excellent"
            loss <= 90 -> "Good"
            loss <= 180 -> "Inaccuracy"
            loss <= 400 -> "Mistake"
            else -> "Blunder"
        }
        return MoveReview(ply, player, san, uci, moveToUci(best), loss, quality)
    }

    private fun searchScore(depth: Int, perspective: Side, alphaStart: Int, betaStart: Int): Int {
        if (status.finished) {
            return when (status.result) {
                "checkmate", "resignation", "timeout" -> if (status.winner == perspective) 100_000 + depth else -100_000 - depth
                else -> 0
            }
        }
        if (depth <= 0) return materialScore(perspective)
        val moves = legalMoves().sortedByDescending { moveOrderingScore(it) }
        if (moves.isEmpty()) return materialScore(perspective)
        var alpha = alphaStart
        var beta = betaStart
        val maximizing = sideToMove == perspective
        var best = if (maximizing) Int.MIN_VALUE else Int.MAX_VALUE
        for (move in moves) {
            val child = copyForSearch()
            child.makeMove(move)
            val score = child.searchScore(depth - 1, perspective, alpha, beta)
            if (maximizing) { best = maxOf(best, score); alpha = maxOf(alpha, best) }
            else { best = minOf(best, score); beta = minOf(beta, best) }
            if (beta <= alpha) break
        }
        return best
    }

    private fun materialScore(perspective: Side): Int {
        val values = mapOf(ChessPieceType.PAWN to 100, ChessPieceType.KNIGHT to 320, ChessPieceType.BISHOP to 335,
            ChessPieceType.ROOK to 500, ChessPieceType.QUEEN to 900, ChessPieceType.KING to 0)
        return board.mapIndexedNotNull { index, piece ->
            piece?.let {
                val material = values.getValue(it.type)
                val center = 7 - (kotlin.math.abs(3 - index / 8) + kotlin.math.abs(3 - index % 8))
                val positional = when (it.type) {
                    ChessPieceType.PAWN -> center * 2
                    ChessPieceType.KNIGHT, ChessPieceType.BISHOP -> center * 4
                    else -> 0
                }
                (if (it.side == perspective) 1 else -1) * (material + positional)
            }
        }.sum()
    }

    private fun moveOrderingScore(move: ChessMove): Int {
        val moving = board[move.from] ?: return 0
        val captured = board[move.to]
        val captureValue = captured?.let { pieceValue(it.type) } ?: if (moving.type == ChessPieceType.PAWN && move.to == enPassant) 100 else 0
        return captureValue * 10 - pieceValue(moving.type) + (move.promotion?.let { pieceValue(it) } ?: 0)
    }

    private fun pieceValue(type: ChessPieceType) = when (type) {
        ChessPieceType.PAWN -> 100; ChessPieceType.KNIGHT -> 320; ChessPieceType.BISHOP -> 335
        ChessPieceType.ROOK -> 500; ChessPieceType.QUEEN -> 900; ChessPieceType.KING -> 20_000
    }

    private fun copyForSearch(): ChessGame = ChessGame().also { copy ->
        copy.board = board.toMutableList()
        copy.castleRights = castleRights.toMutableSet()
        copy.enPassant = enPassant
        copy.halfmoveClock = halfmoveClock
        copy.fullmoveNumber = fullmoveNumber
        copy.repetition = repetition.toMutableMap()
        copy.sideToMove = sideToMove
        copy.moveHistory = moveHistory
        copy.positionHistory = positionHistory
        copy.uciHistory = uciHistory
        copy.status = status
        copy.lastMove = lastMove
    }

    private fun applyMove(move: ChessMove) {
        val piece = board[move.from] ?: return
        val captured = board[move.to]
        if (piece.type == ChessPieceType.PAWN || captured != null) halfmoveClock = 0 else halfmoveClock++
        if (piece.type == ChessPieceType.PAWN && move.from % 8 != move.to % 8 && captured == null) halfmoveClock = 0

        val from = move.from
        val to = move.to
        if (piece.type == ChessPieceType.KING && kotlin.math.abs(to % 8 - from % 8) == 2) {
            if (to % 8 == 6) {
                board[to - 1] = board[to + 1]
                board[to + 1] = null
            } else {
                board[to + 1] = board[to - 2]
                board[to - 2] = null
            }
        }
        if (piece.type == ChessPieceType.PAWN && to == enPassant && captured == null) {
            board[to + if (piece.side == Side.WHITE) 8 else -8] = null
        }
        board[from] = null
        board[to] = if (piece.type == ChessPieceType.PAWN && (to / 8 == 0 || to / 8 == 7)) {
            ChessPiece(piece.side, move.promotion ?: ChessPieceType.QUEEN)
        } else piece

        updateCastleRights(piece, from, to, captured)
        enPassant = if (piece.type == ChessPieceType.PAWN && kotlin.math.abs(to - from) == 16) (to + from) / 2 else null
    }

    private fun updateCastleRights(piece: ChessPiece, from: Int, to: Int, captured: ChessPiece?) {
        when (piece.type) {
            ChessPieceType.KING -> if (piece.side == Side.WHITE) { castleRights.remove("K"); castleRights.remove("Q") }
                else { castleRights.remove("k"); castleRights.remove("q") }
            ChessPieceType.ROOK -> removeRookRight(from)
            else -> Unit
        }
        if (captured?.type == ChessPieceType.ROOK) removeRookRight(to)
    }

    private fun removeRookRight(square: Int) {
        when (square) {
            63 -> castleRights.remove("K")
            56 -> castleRights.remove("Q")
            7 -> castleRights.remove("k")
            0 -> castleRights.remove("q")
        }
    }

    private fun calculateStatus(): GameStatus {
        val moves = legalMoves()
        val checked = isInCheck(sideToMove)
        if (moves.isEmpty()) return if (checked) {
            GameStatus(true, "checkmate", "Checkmate — ${sideToMove.other().label()} wins", sideToMove.other())
        } else GameStatus(true, "stalemate", "Draw by stalemate")
        if (halfmoveClock >= 100) return GameStatus(true, "fifty-move", "Draw by the fifty-move rule")
        if ((repetition[positionKey()] ?: 0) >= 3) return GameStatus(true, "repetition", "Draw by threefold repetition")
        if (isInsufficientMaterial()) return GameStatus(true, "insufficient-material", "Draw — insufficient material")
        return GameStatus(false, "", if (checked) "${sideToMove.label()} to move — check" else "${sideToMove.label()} to move")
    }

    private fun isInsufficientMaterial(): Boolean {
        val nonKings = board.mapIndexedNotNull { index, piece -> if (piece != null && piece.type != ChessPieceType.KING) index to piece else null }
        if (nonKings.isEmpty()) return true
        if (nonKings.size == 1 && nonKings[0].second.type in setOf(ChessPieceType.BISHOP, ChessPieceType.KNIGHT)) return true
        return nonKings.all { it.second.type == ChessPieceType.BISHOP } &&
            nonKings.map { (square, _) -> (square / 8 + square % 8) % 2 }.distinct().size == 1
    }

    private fun isInCheck(side: Side): Boolean {
        val king = board.indexOfFirst { it == ChessPiece(side, ChessPieceType.KING) }
        return king >= 0 && isSquareAttacked(board, king, side.other())
    }

    private fun positionKey(): String {
        val placement = board.joinToString("") { piece ->
            piece?.let {
                val name = if (it.type == ChessPieceType.KNIGHT) "N" else it.type.san.ifEmpty { "P" }
                (if (it.side == Side.WHITE) "w" else "b") + name
            } ?: "-"
        }
        return "$placement|$sideToMove|${castleRights.sorted().joinToString("")}|${enPassant ?: "-"}"
    }

    private fun notationFor(move: ChessMove, piece: ChessPiece): String {
        val file = ('a'.code + move.to % 8).toChar()
        val rank = 8 - move.to / 8
        if (piece.type == ChessPieceType.KING && kotlin.math.abs(move.to % 8 - move.from % 8) == 2) return if (move.to % 8 == 6) "O-O" else "O-O-O"
        val capture = board[move.to] != null || (piece.type == ChessPieceType.PAWN && move.from % 8 != move.to % 8)
        val prefix = if (piece.type == ChessPieceType.PAWN) {
            if (capture) ('a'.code + move.from % 8).toChar().toString() else ""
        } else piece.type.san
        val promotion = move.promotion?.let { "=${it.san}" } ?: ""
        return prefix + (if (capture) "x" else "") + file + rank + promotion
    }

    private fun squareName(square: Int): String = "${('a'.code + square % 8).toChar()}${8 - square / 8}"
    private fun moveToUci(move: ChessMove) = squareName(move.from) + squareName(move.to) + (move.promotion?.san?.lowercase() ?: "")
    private fun parseUci(uci: String): ChessMove? {
        if (uci.length !in 4..5) return null
        val from = parseSquare(uci.substring(0, 2)) ?: return null
        val to = parseSquare(uci.substring(2, 4)) ?: return null
        val promotion = if (uci.length == 5) when (uci[4].lowercaseChar()) {
            'q' -> ChessPieceType.QUEEN; 'r' -> ChessPieceType.ROOK; 'b' -> ChessPieceType.BISHOP; 'n' -> ChessPieceType.KNIGHT
            else -> return null
        } else null
        return ChessMove(from, to, promotion)
    }
    private fun parseSquare(name: String): Int? {
        if (name.length != 2 || name[0] !in 'a'..'h' || name[1] !in '1'..'8') return null
        return (8 - name[1].digitToInt()) * 8 + (name[0] - 'a')
    }

    private fun snapshot() = Snapshot(
        board.toList(), castleRights.toSet(), enPassant, halfmoveClock, fullmoveNumber,
        sideToMove, moveHistory, positionHistory, uciHistory, repetition.toMap(), lastMove, status
    )

    private fun pseudoMoves(position: List<ChessPiece?>, side: Side): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        position.forEachIndexed { square, piece ->
            if (piece == null || piece.side != side) return@forEachIndexed
            val row = square / 8
            val file = square % 8
            fun add(toRow: Int, toFile: Int) {
                if (toRow !in 0..7 || toFile !in 0..7) return
                val to = toRow * 8 + toFile
                if (position[to]?.side != side) moves += ChessMove(square, to)
            }
            when (piece.type) {
                ChessPieceType.PAWN -> {
                    val dir = if (side == Side.WHITE) -1 else 1
                    val nextRow = row + dir
                    if (nextRow in 0..7 && position[nextRow * 8 + file] == null) {
                        addPawnMove(moves, square, nextRow * 8 + file, nextRow)
                        val startRow = if (side == Side.WHITE) 6 else 1
                        val doubleRow = row + dir * 2
                        if (row == startRow && position[doubleRow * 8 + file] == null) moves += ChessMove(square, doubleRow * 8 + file)
                    }
                    for (df in listOf(-1, 1)) {
                        val targetFile = file + df
                        if (targetFile !in 0..7 || nextRow !in 0..7) continue
                        val target = nextRow * 8 + targetFile
                        if (position[target]?.side == side.other() || target == enPassant) addPawnMove(moves, square, target, nextRow)
                    }
                }
                ChessPieceType.KNIGHT -> listOf(-2 to -1, -2 to 1, -1 to -2, -1 to 2, 1 to -2, 1 to 2, 2 to -1, 2 to 1)
                    .forEach { (dr, df) -> add(row + dr, file + df) }
                ChessPieceType.BISHOP, ChessPieceType.ROOK, ChessPieceType.QUEEN -> {
                    val directions = when (piece.type) {
                        ChessPieceType.BISHOP -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                        ChessPieceType.ROOK -> listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                        else -> listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1, -1 to 0, 1 to 0, 0 to -1, 0 to 1)
                    }
                    directions.forEach { (dr, df) ->
                        var r = row + dr
                        var f = file + df
                        while (r in 0..7 && f in 0..7) {
                            val target = r * 8 + f
                            if (position[target] == null) moves += ChessMove(square, target)
                            else {
                                if (position[target]?.side != side) moves += ChessMove(square, target)
                                break
                            }
                            r += dr; f += df
                        }
                    }
                }
                ChessPieceType.KING -> {
                    for (dr in -1..1) for (df in -1..1) if (dr != 0 || df != 0) add(row + dr, file + df)
                    addCastles(position, side, square, moves)
                }
            }
        }
        return moves
    }

    private fun addPawnMove(moves: MutableList<ChessMove>, from: Int, to: Int, row: Int) {
        if (row == 0 || row == 7) {
            listOf(ChessPieceType.QUEEN, ChessPieceType.ROOK, ChessPieceType.BISHOP, ChessPieceType.KNIGHT)
                .forEach { moves += ChessMove(from, to, it) }
        }
        else moves += ChessMove(from, to)
    }

    private fun addCastles(position: List<ChessPiece?>, side: Side, kingSquare: Int, moves: MutableList<ChessMove>) {
        val home = if (side == Side.WHITE) 60 else 4
        if (kingSquare != home || isSquareAttacked(position, home, side.other())) return
        val kingRight = if (side == Side.WHITE) "K" else "k"
        val queenRight = if (side == Side.WHITE) "Q" else "q"
        if (kingRight in castleRights && position[home + 1] == null && position[home + 2] == null &&
            position[home + 3] == ChessPiece(side, ChessPieceType.ROOK) &&
            !isSquareAttacked(position, home + 1, side.other()) && !isSquareAttacked(position, home + 2, side.other())) {
            moves += ChessMove(home, home + 2)
        }
        if (queenRight in castleRights && position[home - 1] == null && position[home - 2] == null && position[home - 3] == null &&
            position[home - 4] == ChessPiece(side, ChessPieceType.ROOK) &&
            !isSquareAttacked(position, home - 1, side.other()) && !isSquareAttacked(position, home - 2, side.other())) {
            moves += ChessMove(home, home - 2)
        }
    }

    private fun isSquareAttacked(position: List<ChessPiece?>, target: Int, bySide: Side): Boolean {
        val tr = target / 8
        val tf = target % 8
        for (square in position.indices) {
            val piece = position[square] ?: continue
            if (piece.side != bySide) continue
            val row = square / 8
            val file = square % 8
            val dr = tr - row
            val df = tf - file
            when (piece.type) {
                ChessPieceType.PAWN -> if (dr == (if (bySide == Side.WHITE) -1 else 1) && kotlin.math.abs(df) == 1) return true
                ChessPieceType.KNIGHT -> if (kotlin.math.abs(dr) * kotlin.math.abs(df) == 2) return true
                ChessPieceType.KING -> if (maxOf(kotlin.math.abs(dr), kotlin.math.abs(df)) == 1) return true
                ChessPieceType.BISHOP -> if (kotlin.math.abs(dr) == kotlin.math.abs(df) && dr != 0 && clearRay(position, row, file, tr, tf)) return true
                ChessPieceType.ROOK -> if ((dr == 0 || df == 0) && (dr != 0 || df != 0) && clearRay(position, row, file, tr, tf)) return true
                ChessPieceType.QUEEN -> if ((dr == 0 || df == 0 || kotlin.math.abs(dr) == kotlin.math.abs(df)) &&
                    (dr != 0 || df != 0) && clearRay(position, row, file, tr, tf)) return true
            }
        }
        return false
    }

    private fun clearRay(position: List<ChessPiece?>, row: Int, file: Int, targetRow: Int, targetFile: Int): Boolean {
        val dr = (targetRow - row).compareTo(0)
        val df = (targetFile - file).compareTo(0)
        var r = row + dr
        var f = file + df
        while (r != targetRow || f != targetFile) {
            if (position[r * 8 + f] != null) return false
            r += dr; f += df
        }
        return true
    }

    private fun applyTo(position: MutableList<ChessPiece?>, move: ChessMove, ep: Int?, movingSide: Side) {
        val moving = position[move.from] ?: return
        if (moving.type == ChessPieceType.KING && kotlin.math.abs(move.to % 8 - move.from % 8) == 2) {
            if (move.to % 8 == 6) { position[move.to - 1] = position[move.to + 1]; position[move.to + 1] = null }
            else { position[move.to + 1] = position[move.to - 2]; position[move.to - 2] = null }
        }
        if (moving.type == ChessPieceType.PAWN && move.to == ep && move.from % 8 != move.to % 8 && position[move.to] == null) {
            position[move.to + if (movingSide == Side.WHITE) 8 else -8] = null
        }
        position[move.from] = null
        position[move.to] = if (moving.type == ChessPieceType.PAWN && (move.to / 8 == 0 || move.to / 8 == 7)) {
            ChessPiece(moving.side, move.promotion ?: ChessPieceType.QUEEN)
        } else moving
    }

    private data class Snapshot(
        val board: List<ChessPiece?>,
        val castleRights: Set<String>,
        val enPassant: Int?,
        val halfmoveClock: Int,
        val fullmoveNumber: Int,
        val sideToMove: Side,
        val moveHistory: List<String>,
        val positionHistory: List<String>,
        val uciHistory: List<String>,
        val repetition: Map<String, Int>,
        val lastMove: ChessMove?,
        val status: GameStatus
    )
}

private fun Side.label() = if (this == Side.WHITE) "White" else "Black"
