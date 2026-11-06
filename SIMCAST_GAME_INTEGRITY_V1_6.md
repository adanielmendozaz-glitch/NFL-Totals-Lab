# NFL Data Center V1.6 — Game Integrity & Player Efficiency

## Alcance

Continúa **NFL Data Center** (applicationId `com.nfltotalslab.datacenter`) a partir de V1.5. No es el código fuente de Pro Stadium V3.1 y no modifica su instalación. Los picks del Core y las apuestas existentes permanecen independientes de Simcast.

### Reglas de simulación

- Arranque de cada mitad mediante evento `KICKOFF`; se sortea el receptor de la primera mitad y el rival recibe la segunda. Se impide que una posesión pase de Q2 a Q3. Los drives sí pueden continuar de Q1 a Q2 y de Q3 a Q4.
- Cambios de campo tras PUNT, FG fallado y pérdidas de balón utilizan posición de campo diferenciada; no reinician siempre a una yarda aleatoria fija. Los kickoff regresan a posiciones de retorno modeladas con valores aproximados.
- Cada touchdown cuenta **seis puntos**. Se almacena por separado `XP_GOOD`, `XP_MISSED`, `TWO_GOOD` o `TWO_FAILED`, y sus respectivos puntos. Los porcentajes iniciales de conversiones son priors de investigación.
- Temporada regular: si Q4 termina empatado, se modela un único periodo adicional de **hasta diez minutos**; ambos equipos tienen oportunidad de poseer una vez, salvo cuando se agota el tiempo. Después se usa muerte súbita modificada. Puede terminar empatado al cumplirse el tiempo.
- El resultado del tiempo extra se registra en `awayOvertime` y `homeOvertime`, sin adulterar los parciales Q1–Q4.
- El auditor `SimcastIntegrity` valida la secuencia de periodos, el kickoff Q3, que no se reutilice un drive de Q2, el orden del reloj, los puntos por tipo de evento, la conciliación de marcadores y las estadísticas básicas. **Los lotes rechazan una simulación inválida, no la cuentan como un resultado.**

### Player Usage

- Team/Player Weekly: se siguen leyendo solo las semanas anteriores al partido, ponderadas sobre cuatro semanas.
- Además de intentos, carreras y targets, se extraen completions, passing_yards, rushing_yards, receptions y receiving_yards cuando las columnas estén presentes.
- La probabilidad de completado y la ganancia de carrera/recepción ahora incluyen **shrinkage** hacia un prior de liga y ajustes acotados por EPA de equipo/rival. Un jugador sin datos de eficiencia utiliza el prior, no se interpreta como 0 completados o 0 yardas.
- El volumen de pase usa un prior regularizado para que el reemplazo de QB no convierta artificialmente al equipo en ofensiva exclusivamente terrestre.
- Las métricas PBP por equipo solo entran si su conteo de partidos no supera los partidos de semanas previas documentados para ese equipo. Las snapshots posteriores al kickoff quedan descartadas cuando detectables.

### JSON / pantallas

- `NFL_SIMCAST_PLAYER_USAGE_V2`: eventos específicos de kickoff/medio tiempo/OT/PAT, `awayOvertime`, `homeOvertime`, integridad y acumulados individuales usados como entrada.
- `NFL_SIMCAST_BATCH_V2`: versión 1.6, `overtimeRate`, `regulationTieRate`, auditor de integridad, indicadores existentes y firma de inputs V16.
- **Data Vault conserva su contenedor V8** para compatibilidad con respaldos históricos; los lotes nuevos se distinguen por el nuevo esquema del JSON interno.
- Pantalla de simulación muestra OT y una columna OT cuando aplica, más un indicador de integridad de reglas.

## Verificación realizada (no equivale a calibración predictiva)

- `tests/test-v16.sh` compila el núcleo puro Kotlin y ejecuta 1,000 partidas individuales + lotes deterministas de 100, 500 y 1,000. El script verifica el medio tiempo, OT, puntos, jugadores excluidos, histogramas y probabilidades que suman 100%.
- Las pruebas pasaron **sin infracciones de las reglas inspeccionadas**.
- En datos *sintéticos de prueba*, el lote de 1,000 con semilla 42 dio total promedio 29.18, Over 39.5 = 16.1%, OT = 7.1% y empates definitivos = 2.5%. **Son cifras de prueba, no un pronóstico verificable**, y el resultado muestra que aún existe una importante necesidad de calibración de puntos y frecuencia de empates.
- **No se compiló la aplicación Android** en este entorno; GitHub Actions debe revisar compatibilidad de Compose/Gradle y generar el APK firmado.

## Advertencias de integridad estadística

1. Una simulación **válida según reglas** no es una predicción estadísticamente acertada.
2. No se ha validado el comportamiento de eficiencia/PPG/OT fuera de muestra contra resultados reales de 2026; el nuevo motor no modifica el Core ni sus recomendaciones.
3. El motor no simula todas las reglas de NFL (penalizaciones completas, defensa individual, retornos, safety, revisiones, físicas 3D ni estrategia especial de 4.ª oportunidad).
4. La fecha del archivo nflverse no prueba la actividad/lesión pregame; los titulares y OUT/IR siguen requiriendo verificación oficial.
5. Para evaluar V1.5 contra V1.6 se requieren lotes **pregame congelados** con los mismos jugadores, estadísticas y lineup, y un conjunto de resultados reales no usado en el ajuste.

## Instalación

`app/build.gradle.kts` mantiene el `applicationId=com.nfltotalslab.datacenter`, ahora `versionCode=38`, `versionName=1.6.0`. La configuración de firma permanente de GitHub Actions permanece sin cambiar. La compilación genera `NFL-Data-Center-V1.6-Game-Integrity` si finaliza correctamente. No desinstalar Pro Stadium ni Data Center anterior: realizar un Data Vault antes de actualizar.

**Fuente de reglas**: [NFL Football Operations · 2026 Rulebook](https://operations.nfl.com/rules-officiating/2026-nfl-rulebook) y [Overtime Rules](https://operations.nfl.com/rules-officiating/featured-rules/overtime-rules/).
