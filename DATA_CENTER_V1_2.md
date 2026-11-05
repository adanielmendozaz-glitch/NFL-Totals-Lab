# NFL Totals Lab 1.2 · Data Center / Simcast Visual Lab

## Alcance real de esta entrega

Es el **código fuente** de NFL Totals Lab (Android Kotlin/Compose) con las mejoras
nuevas. **No es el código fuente de Pro Stadium V3.1**, que llegó en un ZIP con
un APK compilado. No reemplaza el Simcast de libGDX.

### Datos · funcionalidad añadida

- Ajustes > ABRIR DATA CENTER: cuatro importaciones manuales CSV **separadas**:
  Team Ratings (The Margin), Team Season Stats (nflverse), Player Season Totals
  (nflverse), Player Weekly Stats (nflverse).
- Botón SINCRONIZAR 4 FUENTES: intenta descargar los archivos CSV oficiales;
  al abrir la app hace un intento en segundo plano si han transcurrido 12 h.
  La disponibilidad de los archivos depende de los publicadores y de la red.
- Valida temporada, columnas, formato, tamaño mínimo, cantidad de registros,
  semana, SHA-256 y evita sustituir una fuente por otra más antigua.
- Snapshot actual de cada fuente en SQLite, con origen, fecha y huella SHA-256.
  Conserva el historial de importaciones (metadatos, no todos los CSV previos).
- Data Vault V6: exporta las cuatro fuentes con todas sus columnas del CSV
  actual y el historial de importación. Restore respeta datos existentes.
- El PBP de nflverse **sigue siendo la fuente principal** del Core cuando hay
  suficiente muestra (>= 2 juegos y >= 100 jugadas por equipo).
  The Margin EPA funciona como **respaldo** cuando falta PBP; Team Stats como
  respaldo de volumen. Se usan solo si los datos son de semana anterior
  al partido. Player Totals y Weekly se guardan para la futura integración
  en Player Usage de Simcast. No afectan todavía al pronóstico oficial.
- Todas las predicciones nuevas generan `inputKey` con los valores efectivamente
  empleados. No se recalculan predicciones de partidos iniciados/finalizados.

### Visual · prueba de concepto

- Ajustes > VER PREVIEW SIMCAST: cancha 2D original con 22 círculos/jugadores
  animados, balón, velocidad y controles reproducir/pausa/avanzar jugada.
- **NO ES UNA SIMULACIÓN REAL**: jugadas y puntuaciones del preview son
  demostrativas, sin Markov Drive ni Player Usage. No se puede usar para apostar.
- La conexión real con el simulador Pro Stadium requiere recuperar su código
  fuente o reconstruir su motor visual/libGDX de forma independiente.

## Fuentes descargables

- The Margin EPA: https://themarginsports.com/v1/nfl/team-epa.csv
- nflverse Team Season Stats (ej. 2026):
  https://github.com/nflverse/nflverse-data/releases/download/stats_team/stats_team_reg_2026.csv
- nflverse Player Season Totals (ej. 2026):
  https://github.com/nflverse/nflverse-data/releases/download/stats_player/stats_player_reg_2026.csv
- nflverse Player Weekly Stats (ej. 2026):
  https://github.com/nflverse/nflverse-data/releases/download/stats_player/stats_player_week_2026.csv

La descarga de nflverse **no es en vivo**. Los cuatro archivos pueden actualizarse
con distinta frecuencia. Un CSV de temporada puede no incluir una columna
`through_week`; en ese caso la app estima la última semana de resultados finales
según el calendario local, de forma conservadora. Si todavía no hay calendario,
puede mostrar `week 0` y no activar ese respaldo en las proyecciones.

## Compilación

El ZIP contiene el proyecto Android completo y `.github/workflows/android-apk.yml`.
**No contiene APK recompilado.** Para compilar en GitHub, copia el contenido del
proyecto a tu repositorio (incluida la carpeta `.github`), haz commit y push.
El workflow ejecuta Gradle 8.9 con Java 17 / SDK 35, y sube un artifact
`NFL-Totals-Lab-V1.2-Data-Center`.

Si tu repositorio ya tiene cambios más recientes que esta base, haz un backup
antes de copiar; compara y combina archivos para no pisar mejoras posteriores.
La firma definitiva solo se conserva si existen y son válidos los secretos
`NFL_KEYSTORE_BASE64`, `NFL_STORE_PASSWORD`, `NFL_KEY_ALIAS`, `NFL_KEY_PASSWORD`.
Sin ellos, Gradle podría generar un debug APK con firma diferente, que Android
no permitirá instalar encima de otra app firmada con una clave distinta.

## Checklist de pruebas que faltan en Android real

1. Abrir Ajustes y entrar en Data Center y Visual Lab.
2. Importar archivos 2026 de los cuatro tipos; verificar filas/semana/huella.
3. Importar de nuevo el mismo CSV: no debe generar duplicados.
4. Importar un CSV de otra temporada o sin columnas obligatorias: debe fallar.
5. Activar sincronización remota y revisar los errores parciales por fuente.
6. Exportar Data Vault V6 e importarlo en instalación de prueba; revisar feeds.
7. Comparar predicciones pregame con y sin PBP de suficiente tamaño. Confirmar
   que partidos con kickoff alcanzado no admiten predicción nueva.
8. Auditar precisión fuera de muestra antes de promover cualquier ajuste extra.

## Límites

- Aún no hay verificación de compilación Android en este entorno (no hay SDK Android/Gradle).
- No se asegura una mejora de precisión solo por añadir CSV; se necesita auditoría
  con pronósticos congelados, Brier y error en puntos.
- No se guardan CSV históricos completos de cada reimportación: únicamente el
  snapshot actual íntegro y la trazabilidad (SHA, fecha, semana) de los anteriores.
