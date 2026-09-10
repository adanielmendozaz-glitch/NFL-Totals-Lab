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

## V0.3 · Integrity Fix

- Corrige turnover por jugada -> probabilidad por drive.
- Separa RNG de turnover y scoring.
- Liquida apuestas automáticamente al sincronizar finales.
- Bloquea análisis de partidos ya finalizados.
- Conserva Ranking/Censo/Apuestas/Bank en SQLite local.

## V0.4 · Auto Census Core

- SINCRONIZAR analiza automáticamente todos los juegos no finalizados de la jornada REG activa con línea O/U.
- AUTO_CENSUS no crea apuestas: solo alimenta Censo, Ranking y CORE.
- Deduplicación por inputs: si línea y métricas no cambian, no crea snapshots repetidos.
- Si cambian la línea o métricas efectivamente usadas por el motor, conserva un nuevo snapshot auditable.
- CORE muestra un registro vigente por partido y contadores AUTO/MANUAL/FINAL.
- Brier usa una sola predicción vigente por partido para no inflar calibración por reanálisis.
- Migración SQLite V2 -> V3 es aditiva y conserva historial, apuestas y bank.
- Los pesos de motores permanecen intactos.
- GitHub Actions fija y reutiliza la debug signing key desde V0.4 para que futuras APK puedan actualizarse sin borrar SQLite.

## V0.4.1 · Fast Sync + Live Tracker

- FAST SYNC carga primero calendario, líneas, resultados y liquidaciones.
- La jornada aparece antes de iniciar el procesamiento pesado de PBP.
- DEEP DATA guarda métricas y las reutiliza durante 4 horas.
- Auto Census sigue separado de Apuestas.
- LIVE Tracker muestra marcador, estado, cuarto/reloj y total actual.
- LIVE refresca cada 60 segundos solo mientras está activado.
- El marcador LIVE no modifica ni reentrena el pick pregame.
- Si falla el feed LIVE, el Core y SQLite siguen funcionando.

## V0.4.2 · Permanent Signing Baseline

- La keystore privada ya no depende del runner ni de Actions cache.
- La clave vive en GitHub Actions Secrets y en el backup privado de Termux.
- El repositorio público no contiene la clave privada.
- V0.4.2 es la línea base de firma para futuras actualizaciones.
- Fast Sync, Live Tracker, Auto Census, Core y Brier permanecen intactos.
