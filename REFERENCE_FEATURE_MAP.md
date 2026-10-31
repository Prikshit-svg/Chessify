# Android chess reference: feature map

## Artifact inspected

- XAPK: `Chess+-+Play+and+Learn+Online_4.10.25-googleplay_APKPure.xapk`
- Package: `com.chess`, version `4.10.25-googleplay` (version code `280110`)
- XAPK size: 165 MiB; it contains a 121 MiB base APK, an arm64 split, and locale/density splits.
- The base APK contains 196 bot-avatar assets. Chessifyy does not reuse those portraits or bot identities.
- SHA-256: `28fd52176ff156e9d7029344cfdf198815462031cc0e30fff7bc668bd7a298f1`

## Feature areas observed in package metadata

Static resource names, class strings, and local database schema identifiers point to these product areas:

- Live play, challenges, daily games, bot and coach games, time controls, ratings, player profiles, and game history.
- Daily puzzles, puzzle streaks, relative puzzle ratings, puzzle themes, and puzzle coaching feedback.
- Lessons and courses, opening/endgame training, endgame drills, and coach prompts.
- Post-game review with move-quality labels and accuracy reporting.
- Tournaments with pairings, standings, and game records.
- Clubs, friends, conversations, direct messages, notifications, and activity feed.
- Awards, profile statistics, and play streaks.

These are package-level indicators, not a complete runtime walkthrough. The XAPK was not installed or launched for this inspection. Its embedded code and assets are reference material only; Chessifyy will use original implementation and visuals. Although the reference declares Google Play Billing, Chessifyy remains free and does not include payment flows.

## Chessifyy coverage

| Area | Current state |
| --- | --- |
| Chess play | Local rules engine, four original offline bot profiles with different styles/strengths, clocks, side selection, tap and drag moves, synthesized move/capture/check/game-end sounds, move history |
| Puzzles | Four original local mate-in-one exercises; rotating daily puzzle and saved daily solve streak |
| Lessons | Three local lessons with saved completion |
| Position analysis | Local candidate lines, FEN loading, approximate evaluation |
| Ratings | Local Rapid/Blitz/Bullet ratings and provisional estimates against bot tiers |
| Game review | Opens after each finished game; up to 20 saved games, replay, move labels (Brilliant/Great/Best/Excellent/Good/Book Move/Inaccuracy/Mistake/Blunder), approximate accuracy, and a Chess.com-style one-game performance estimate using original heuristics |
| Tournaments | Saved three-round practice series against the bot; no online pairings/standings |
| Clubs and chat | Local-only club names and notes; no accounts or cross-device sync |
| Friends and live play | Not implemented; requires a hosted Chessifyy service |
| Endgame training, awards, activity feed | Not implemented |

## Next implementation priorities

1. Improve the bot and review engine, and calibrate bot/performance estimates through actual game data.
2. Expand original puzzle and lesson content while preserving the daily streak flow.
3. Build a Chessifyy backend for accounts, live games, chat, clubs, and online tournament pairings. The package alone does not supply a usable Chessifyy service or credentials.
