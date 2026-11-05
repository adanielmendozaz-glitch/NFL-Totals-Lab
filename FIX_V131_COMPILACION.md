# NFL Data Center V1.3.1 — reparación de compilación

Corrige los tres errores Kotlin detectados en GitHub Actions #81:

- `SimcastGameScreen.kt:129`: `onClick={runScenario}` → `onClick={runScenario()}`.
- `SimcastGameScreen.kt:210-211`: dos llamadas a `QuarterLine` (extensión `RowScope`) ahora se ejecutan dentro de un `Row` independiente por equipo.
- Se incrementa versión Android `versionCode` 34 → 35; `versionName` 1.3.0 → 1.3.1.
- Mantiene `applicationId=com.nfltotalslab.datacenter`, preservando separación de Pro Stadium.

El compilador Android debe volver a verificarse en GitHub Actions. No hubo otras alteraciones de la lógica estadística.
