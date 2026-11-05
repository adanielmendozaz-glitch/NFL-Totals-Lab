# NFL Data Center 1.5 — Simulation Center / Calibration & Audit

## Funciones incorporadas

- En **Ajustes → Simcast Player Usage** o entrando al simulador desde un partido de Jornada: botones **100 / 500 / 1,000** y **SIMULAR N PARTIDOS**.
- Progreso y **CANCELAR LOTE**, sin guardar ejecuciones parciales.
- Distribución de totales por intervalos de cinco puntos; medias, mediana, desviación, percentiles P10/P25/P75/P90, diez marcadores más frecuentes, victorias y OVER/UNDER/PUSH si existe línea.
- Auditor de drives por equipo: pases, acarreos, TD, FG, punts y turnovers promedio, además de promedio y P90 de yardas por jugador, intentos, acarreos y targets.
- Reproduce y permite exportar **una** corrida del lote con las animaciones V1.4. El motor no crea 1,000 animaciones en memoria: solo resultados agregados y un replay.
- Línea de jugadores: respeta las selecciones QB1 y OUT/IR realizadas en la ficha del partido.
- Calibración pregame ligera: resultados de partidos de **semanas anteriores** y shrinkage hacia la media liguera, con proporciones de eficiencia moderadas; la cadencia de drives solo utiliza métricas PBP cuando su número de partidos no supera los partidos completados previamente.
- Identificador SHA-256 de entradas: semanas/hashes de los CSV, valores relevantes de métricas y titulares/bajas. Incluido en JSON de lotes.
- **Data Vault V8**: exporta hasta las cinco últimas simulaciones masivas por partido; mantiene las tres corridas individuales anteriores, CSV, apuestas, predicciones y calibración previa. SQLite migra del esquema 9 al 10 sin eliminar datos.
- **Auditor histórico**: calcula MAE y Brier usando el último lote **PRE-GAME** guardado por partido que después tenga resultado final; lotes posteriores al kickoff se marcan NO PREGAME y no se califican.

## Límites y advertencias

- La V1.5 es **experimental**, independiente del pronóstico oficial de NFL Totals Lab Core. Ningún porcentaje de Simcast sustituye al Core ni demuestra por sí solo precisión.
- La corrección de PPG usa puntos finales de partidos anteriores. Las estadísticas completas PBP/roster pueden ser snapshots sin fecha congelada; el producto no declara validez de backtest estricto sin registros de entrada pregame.
- `Team Season Stats` y `Player Season Totals` pueden indicar semana 0 si falta fecha de corte: Player Usage sigue tomando las filas `Player Weekly Stats` con semana anterior al partido.
- La información OUT/IR y QB1 **no se confirma automáticamente** para todos los partidos. Se mantiene formulario manual con procedencia; no inferir lesiones ni elegibilidad de acumulados.
- No se incluye integración libGDX 3D ni reproducción física de jugadas: se conserva el visualizador 2D mejorado de V1.4.
- No se garantiza mejora de precisión sin auditoría fuera de muestra y snapshots previos a kickoff.

## Verificaciones efectuadas

- Kotlin CLI: compilación de `Models.kt`, `PlayerUsageDriveEngine.kt`, `SimcastCalibration.kt`, `SimcastBatchEngine.kt`.
- Corridas deterministas y pruebas de integridad para lotes de 100, 500 y 1,000.
- Verificación de sumas OVER+UNDER+PUSH = 100 %, probabilidades de ganador = 100 %, conteo del histograma, percentiles ordenados, puntos por cuarto y exclusión OUT/IR en la simulación de prueba.
- **No se ha compilado el APK Android en este entorno** (sin SDK Android/Gradle); GitHub Actions verificará Compose y la integración del APK.

## Compilación

- Identificador Android: `com.nfltotalslab.datacenter`, para instalar junto a Pro Stadium V3.1 y encima de la app Data Center anterior.
- Versión `1.5.0` (`versionCode 37`), mantiene firma Android definida por los mismos secretos GitHub Actions previos.
- El archivo ZIP contiene el código fuente, no una APK firmada.
- El workflow publica artifact `NFL-Data-Center-V1.5-Simulation-Auditor` si termina en verde.
