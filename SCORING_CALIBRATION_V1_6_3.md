# NFL Simcast V1.6.3 — Scoring Calibration SHADOW

## Qué cambia

Se instala junto a la V1.6.2, manteniendo Data Center, Player Usage, titulares, lesiones, el Simcast visual y el Core. En el Simulation Center, después de simular 100/500/1000 juegos, se muestra un bloque **MARCADOR · CALIBRACIÓN ESTADÍSTICA (SHADOW)** con una segunda distribución de puntos. Es una estimación separada del motor de jugadas y NO se presenta como marcador de su animación.

## Fuentes con corte pregame

1. Marcadores finales de partidos de temporada regular anteriores a la semana del encuentro. `PregameSimcastCalibration` aplica una regularización hacia la media de liga, utilizando ofensiva propia y puntos permitidos del contrario. Los marcadores del partido objetivo NO entran en su entrenamiento.
2. Datos PBP del mismo equipo (TD/drive y FG/drive) únicamente cuando `TeamMetrics.games` no supera la cantidad de partidos finalizados antes del encuentro y el registro es suficiente. Se suavizan con 50 posesiones de prior para evitar muestras pequeñas. Cuando faltan datos válidos se utilizan tasas de prior, señaladas como tales.
3. EPA de ataque propio y EPA permitido del rival, solo desde datos con corte temporal válido y con un impacto limitado para evitar contar dos veces la fuerza de equipo.
4. Jugadores Player Weekly hasta la semana anterior; el QB anunciado o seleccionado se compara mediante yardas por intento regularizadas hacia el prior, no se inventa producción cero para suplentes.
5. Las lesiones OUT/IR de QB y jugadores ofensivos elegibles afectan la proyección cuando coinciden con la muestra semanal; las ausencias defensivas, de línea ofensiva, bajas no identificadas, clima y cambios en tiempo real **NO** están calibrados por esta versión. Los QUESTIONABLE no se eliminan automáticamente.

## Estimación

- Calibración conservadora de puntos esperados por equipo frente a la defensa rival.
- Tasas de touchdowns y goles de campo por posesión, suavizadas hacia priors; escaladas con los puntos esperados del enfrentamiento **obtenidos de resultados anteriores**, nunca con la línea de apuestas.
- En cada corrida se simulan posesiones por equipo con ritmo compartido y variación de eficiencia entre partidos. Los touchdowns producen seis puntos más intento extra simulado.
- Se calculan marcadores promedio, total, mediana, desviación, P10–P90, frecuencia de marcadores y probabilidades exactas OVER/UNDER/PUSH para la línea introducida.
- El archivo de auditoría añade `scoringShadow` con parámetros, avisos, fuentes y distribuciones. El auditor de lotes pregame finalizados compara MAE y Brier del Simcast y del modelo Shadow sin entrenar sobre el mismo partido.

## Integridad y validación

- `bash tests/test-v16.sh`: verifica 1000 partidos del simulador de jugadas, inicio correcto de Q3, overtime, exclusión de jugadores descartados y conciliación de scores.
- `bash tests/test-scoring-v163.sh`: verifica lotes 100/500/1000, reproducibilidad con la misma semilla, percentiles y probabilidades, que la línea de mercado NO afecta la proyección y que el cambio de QB y lesión ofensiva tiene efecto limitado.
- Estas pruebas **NO** demuestran que la puntuación sea predictivamente precisa. Antes de utilizarla para apuestas se necesita validación walk-forward en partidos completos de varias jornadas, con entradas bloqueadas antes de kickoff; comparar MAE, sesgo por equipo, Brier y calibración del O/U. No promover automáticamente al Core tras un solo buen juego.

## Limitaciones

- Las jugadas animadas siguen siendo las simulaciones del motor Player Usage V1.6.x; no se alteran ni se inventan eventos para forzar el marcador Shadow. Son modelos paralelos que pueden diferir.
- Las probabilidades Shadow son experimentales hasta validación histórica suficiente.
- No se incorporan datos futuros. Archivos Team/Player Season Totals con semana 0 o fecha de corte incierta siguen excluidos de simulaciones retroactivas.
- Un QB suplente sin muestra tiene **mayor incertidumbre**, no se castiga con un puntaje ficticio. La eficiencia real del QB3 requiere datos de entrenamiento que la fuente no siempre proporciona.

## Subida

Subir el proyecto completo desde Termux al repositorio NFL-Totals-Lab; versión Android `1.6.3`, `versionCode 41`, mismo `applicationId com.nfltotalslab.datacenter`. Para compilarlo e instalarlo se requiere ejecutar el workflow Android de GitHub Actions. El compilador Android no estuvo disponible en este entorno; las verificaciones fueron pruebas Kotlin aisladas.
