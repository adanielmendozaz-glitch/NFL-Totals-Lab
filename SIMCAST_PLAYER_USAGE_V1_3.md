# NFL Data Center V1.3 — Simcast Player Usage

## Instalación y seguridad

- Código fuente Android, **no** un APK ya compilado.
- `applicationId = com.nfltotalslab.datacenter`, `versionCode = 34`, `versionName = 1.3.0`.
- Se actualiza sobre **NFL Data Center V1.2.1**, y permanece independiente de **NFL Pro Stadium V3.1** (`com.nfltotalslab.app`).
- Conserva la configuración de firma del workflow existente. No desinstalar ninguna app ni borrar datos al probar.
- Migra base SQLite versión 8 → 9 creando tabla `simcast_runs`, sin eliminar tablas antiguas.

## Simulación experimental (pantalla nueva)

- Jornada → partido → **SIMULAR ... PLAYER USAGE**; o Ajustes → **ABRIR SIMCAST DE PARTIDOS**.
- Selección de semana y encuentro: visitante y local, todos los partidos disponibles en el calendario del repositorio.
- Simulación individual por partido: drives alternados, downs y distancia, pase, acarreo, sack, intercepción, fumble, punt, FG y touchdown. Marcador dinámico y por cuartos.
- Animación ilustrativa con 22 círculos, trazado aproximado de campo, indicador del balón, velocidad 1x–8x, avanzar/retroceder, pausar y saltar al resultado.
- Jugadores de cada equipo seleccionados mediante datos **Player Weekly Stats** de nflverse que ya carga Data Center; roles QB, RB/FB, WR/TE y pesos de targets/carries/attempts.
- Se excluyen semanas `>= game.week` y se utilizan como máximo las cuatro semanas anteriores, con ponderación temporal `0.72^(distancia en semanas)`. No se usan Player Season Totals sin fecha de corte; un acumulado `semana 0` sigue identificado como `semana de corte no determinada`.
- Estadísticas en la corrida: intentos/completados/yardas/INT/TD de pase, acarreos/yardas/TD, targets/recepciones/yardas/TD, resumen por equipo y por cuarto.
- Se etiquetan QB/corredor/receptor sin datos cuando no hay histórico semanal; NO se inventan nombres. No comprueba titularidad ni lesiones.
- Cada corrida tiene semilla (`seed`) y se puede repetir de forma determinista; exportación JSON propia.
- SQLite guarda últimas **3 corridas por partido**. Exportación general Data Vault V7 incluye estas corridas (`simcast_runs`) y restauración sin sobrescribir registros existentes.
- El modelo principal `TotalsEngine` **NO se sustituye ni se recalibra**, y el Simcast no altera probabilidades/picks en Censo, Core, Shadow o apuestas.

## Límites honestos

- No es el código original de Pro Stadium libGDX. Esta nueva pantalla sigue utilizando Android Jetpack Compose Canvas.
- No son las jugadas reales ni reconstrucciones del play-by-play oficial. El motor drive/down-distance genera eventos sintéticos y el render visualiza rutas ilustrativas.
- Una sola corrida NO estima probabilidades ni valida un pronóstico. Para estimación Over/Under se necesitan muchas simulaciones con calibración prospectiva y auditoría fuera de muestra.
- Las métricas PBP históricas almacenadas en la app podrían incluir información posterior si se simula un partido ya terminado; se advierte en pantalla. No considerar como backtest pregame.
- No se ha ejecutado un build Android aquí por falta de SDK/Gradle; la compilación se debe verificar en GitHub Actions.

## Pruebas realizadas

- Compilación standalone del motor puro con Kotlin 1.9.x (`kotlinc`) y 200 simulaciones sintéticas del mismo partido.
- Verificado: juego determinista por semilla, orden de jugadas, marcador no negativo, jornadas y cuartos; datos de ejemplo en torno a 41 puntos combinados de media (esto NO prueba predicción real).

## Checklist de pruebas tras instalación

1. En Ajustes → Data Center verificar que Player Weekly 2026 tenga semana >0 (ej., 8).
2. Jornada → seleccionar un juego de semana 9 → SIMULAR PARTIDO. Confirmar nombres reales de ambos equipos y columnas de Player Usage.
3. Comprobar primer y último evento, marcador y suma de puntos por cuartos.
4. Alternar entre el panel estadístico visitante/local y verificar que no haya jugadores de un equipo asignados al rival.
5. Exportar el JSON de un partido, exportar Data Vault V7 y comprobar la presencia de `simcast_runs`.
6. Comprobar que Core y apuestas sean idénticos antes y después de simular.
7. Verificar que Pro Stadium V3.1 todavía está instalado y sus datos no cambiaron.
