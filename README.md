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

## V0.5 · Shadow Lab + Calibration

- Shadow Lab persiste la salida individual de cada motor del Core.
- Cada Shadow queda congelado pregame y nunca modifica el pick oficial.
- Deep Sync evita crear Core/Shadow cuando LIVE ya marca IN/POST.
- LIVE FINAL persiste el marcador y liquida Core + Shadow + Apuestas.
- FAST SYNC también liquida Core + Shadow si LIVE estuvo apagado.
- Brier usa un cierre oficial por juego.
- Calibration añade rangos 50–54.9, 55–59.9, 60–64.9, 65–69.9 y 70%+.
- Shadow Brier mide cada motor por separado.
- SQLite DB3 -> DB4 es aditiva: conserva Censo, Core, Apuestas y Bank.

## V0.6 · Audit Lab

- ROI teórico a cuota decimal 1.91 y stake uniforme de 1u.
- Hit rate, W-L-P, Brier y probabilidad media.
- ECE (Expected Calibration Error) sobre buckets de probabilidad.
- Auditoría 50–54.9%, 55–59.9%, 60–64.9%, 65–69.9% y 70%+.
- Auditoría OVER vs UNDER.
- Auditoría PASS / LEAN / JUGABLE.
- Rendimiento del Core por equipo.
- Comparación Core Ensemble vs cada motor Shadow.
- Audit Lab es observacional: no altera pesos ni picks.
- LIVE FINAL refresca inmediatamente Core, Shadow, Brier y Audit.
- Sin migración de base de datos: conserva íntegro SQLite V0.5.

## V0.6.1 · Visual Pack

- Branding para los 32 equipos NFL.
- Logos externos en runtime; no se guardan dentro del repo.
- Fallback automático a badge propio si un logo falla.
- Core, Shadow, Brier y Audit permanecen intactos.

## V0.6.2 · Visual Polish + Integrity

- Logos en Jornada, Ranking, Core, Censo, Shadow, Equipos y filas de equipo de Audit.
- Marcadores con contraste alto; el color del equipo queda como acento.
- Conserva un marcador FINAL capturado por LIVE aunque nflverse tarde en actualizar.
- No cambia pesos, motores, umbrales, Brier ni lógica Shadow.
