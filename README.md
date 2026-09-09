# NFL Totals Lab V0.2 — Native Android

No HTML. No Netlify.

Android nativo en Kotlin + Jetpack Compose con persistencia SQLite.

## Arquitectura NFL
- Jornada nativa por tarjetas.
- Ranking persistente.
- Equipos: EPA/play, EPA defensivo, drives/G, TD/drive, Success Rate, explosive rate.
- Censo persistente.
- Apuestas.
- Bank.
- Brier LAB.
- Auto-liquidación del Censo al sincronizar resultados finales.
- SQLite Data Vault local.

## Fuentes
La app consulta nflverse:
- Schedule: `nflverse-data / schedules / games.csv`
- Play-by-play: `play_by_play_<season>.csv.gz`
- Rosters: `roster_<season>.csv`
- Injuries: `injuries_<season>.csv`

## Motores
- Markov Drive 30%
- Negative Binomial 25%
- Drive Monte Carlo 20%
- Bayesian 15%
- Shadow Poisson 10%

100,000 simulaciones por motor.

## Gating
- JUGABLE ★ >= 66%
- LEAN >= 60%
- PASS < 60%

## Primer arranque
La app abre aunque no haya internet ni datos. Pulsa `SINCRONIZAR`.
La primera sincronización de PBP puede tardar porque el archivo de play-by-play se descarga y procesa en streaming.

## Compilación
El repositorio incluye `.github/workflows/android-apk.yml`.
Cada push a `main` compila automáticamente un APK debug y lo deja en:
GitHub > Actions > Build Android APK > Artifacts.

## Nota de diseño
Esta versión es la base nativa. El siguiente paso es añadir:
- depth chart / inactivos,
- impacto por posición (QB/OL/WR/etc.),
- clima live,
- market-line movement,
- Shadow Mode / Gating Mode avanzados,
- backup/importación externa del Data Vault.
