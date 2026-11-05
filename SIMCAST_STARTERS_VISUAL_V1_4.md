# NFL Data Center V1.4.0 — Titulares e interfaz de jugadas

## Alcance
- Campo animado en Compose Canvas: formaciones diferenciadas, desplazamientos ofensivos y defensivos, trayectoria de balón y jugadas visualmente distintas para pase, carrera, sack y despeje/FG. **No es un motor físico 3D ni una reproducción histórica del encuentro.**
- Fijar QB1 por partido y excluir nombres introducidos como OUT/IR de las estadísticas individuales y de las jugadas.
- Para JAX @ BAL (temporada 2026, semana 9) incluye un snapshot fechado 2026-11-05: QB1 Joe Fagnano, OUT/IR Lamar Jackson y Tyler Huntley, según comunicados oficiales; no usar este snapshot en otros partidos.
- No excluye automáticamente a los cuestionables, como Bhayshul Tuten (JAX) para la semana 9.
- Cuadro PREPARTIDO editable para cualquier partido. Campo en blanco = QB elegido por sus intentos recientes (NO confirmado). Nombres editados se marcan SIN VERIFICAR.
- Enlace directo al injury report de NFL; **todavía no se importa automáticamente el informe completo ni se confirma el listado de inactivos 90 minutos antes del juego.**
- JSON de simulación y Data Vault incluyen QB indicado, out y fuente; sin afectar el Core.

## Fuentes fechadas
- https://www.baltimoreravens.com/news/lamar-jackson-tyler-huntley-injured-reserve-austin-reed-adisa-isaac-jake-moody
- https://www.baltimoreravens.com/team/injury-report/
- https://www.nfl.com/news/ravens-to-start-undrafted-rookie-qb-joe-fagnano-vs-jaguars-on-thursday
- https://www.nfl.com/news/nfl-week-9-injury-report-2026-season

## Validación
- Motor Kotlin: prueba 100 simulaciones JAX@BAL, sin intentos ni estadísticas asignados a Lamar Jackson ni Tyler Huntley; Fagnano único lanzador de Baltimore.
- Compilación Android completa y prueba de UI real: pendientes de GitHub Actions en rama main.
- No se han ajustado parámetros empíricos de eficiencia de un QB novato. El modelo todavía NO debe utilizarse para probabilidades de apuestas sin auditoría fuera de muestra.
