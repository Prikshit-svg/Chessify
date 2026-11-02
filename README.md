# Chessifyy

Chessifyy is an Android chess app with an original, Lichess-inspired interface. The app is free and contains no payment or subscription flow.
Here is the link to its apk file :- https://drive.google.com/file/d/1G139wMKOYbrShhiEvQ0jPW87UZpaZ889/view?usp=drive_link
## Implemented in the Android app

- Local chess games with legal move validation, castling, en passant, promotion, checkmate, stalemate, draw rules, resignation, clocks, board flip, and move history.
- Drag pieces to legal destination squares as well as tap-to-select; original synthesized sounds distinguish moves, captures, checks, and game endings.
- Four offline bot profiles with distinct search depth, play style, and provisional ratings; choose your side before play.
- Locally saved Rapid, Blitz, Bullet, and puzzle ratings. Computer game results use provisional bot ratings; separate performance estimates are shown per time control with small-sample smoothing. These ratings need calibration against real players.
- Tactics puzzles with hints, a rotating daily puzzle, saved completion progress, and a daily solve streak.
- Short lessons with interactive knowledge checks and saved completion progress.
- Position analysis board with FEN loading, selectable search depth, three candidate moves, short principal lines, centipawn estimates, and an approximate White expected-score percentage.
- Automatically opened post-game review and an archive of the 20 most recent games, with position replay, per-move local engine comparison, Brilliant/Great/Best/Excellent/Good/Book Move/Inaccuracy/Mistake/Blunder labels, approximate accuracy, and a Chess.com-style one-game performance estimate. These estimates are original heuristics, not Chess.com calculations or ratings.
- A saved three-round practice series against the computer.
- On-device club drafts and chat notes.

## Service-dependent features

Live matchmaking, friend challenges across devices, online ratings, shared clubs, live chat, and tournaments with other players need a hosted Chessifyy backend. Those screens currently identify this requirement and do not claim to send or receive online data. No backend URL, hosting account, or server credentials have been supplied, so these features cannot be made live from this project alone.

## Build

Open this directory in Android Studio and sync Gradle. The current project uses Android Gradle Plugin 9.2.1, Kotlin 2.2.20, and Jetpack Compose. To build the debug APK from a shell:

```sh
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew assembleDebug
```

No Android device is needed for this build. Device installation or testing is intentionally deferred until requested.
