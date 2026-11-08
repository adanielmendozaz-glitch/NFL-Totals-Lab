# NFL Data Center V1.6.2 — Week 9 QB & Injury Snapshot

- 14 unstarted week-9 fixtures as of Nov 8, 2026 at 08:10 America/Monterrey.
- QB1 defaults by fixture. Confirmed replacement QB1: CLE Shedeur Sanders, PHI Joe Milton III, SEA Drew Lock, MIN Carson Wentz. Others expected from weekly depth chart, not verified gameday starters.
- NFL Week 9 final OUT/QUESTIONABLE/DOUBTFUL lists, and identified long-term IR for key players. This does **not** imply all NFL injured reserve players were exhaustively found.
- OUT / identified IR are excluded from Player Usage; QUESTIONABLE and DOUBTFUL are visible but not removed pending actual game inactives.
- Name normalization handles Jr., periods, apostrophes and roman numeral suffixes for QB and OUT matching.
- Game CIN@ATL is neutral site in Madrid; banner present but current engine does not implement home advantage in the first place.
- This is an OFFLINE SNAPSHOT, NOT auto-retrieval or live verification. User must check 90-minute inactives.
- Core is unchanged. Only Simcast defaults/usage filters and UI text have been modified.
- Source: https://www.nfl.com/news/nfl-week-9-injury-report-2026-season
- QB source: https://www.si.com/nfl/every-starting-nfl-quarterback-2026-season-updated-weekly and club confirmations.
- Snapshot JSON: NFL_WEEK9_QB_INJURY_SNAPSHOT.json
- Previous game JAX@BAL remains supported by its existing original code.
- Android build must be verified through GitHub Actions; this package has not been compiled as APK.
