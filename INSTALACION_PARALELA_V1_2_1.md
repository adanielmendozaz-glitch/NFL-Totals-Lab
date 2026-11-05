# NFL Data Center V1.2.1 — instalación paralela

- **applicationId:** `com.nfltotalslab.datacenter`
- **namespace Java/Kotlin:** `com.nfltotalslab.app` (se conserva deliberadamente, no es el identificador de instalación)
- **versión:** 1.2.1 (`versionCode` 33)
- **etiqueta de la aplicación:** NFL Data Center
- **actividad principal:** `com.nfltotalslab.app.MainActivity`

## Motivo

Pro Stadium V3.1 usa `com.nfltotalslab.app` (`versionCode` 40), mientras que la V1.2 tenía `versionCode` 32 con el mismo `applicationId`. Por tanto Android puede rechazar la actualización como downgrade; además, sobrescribir la aplicación anterior no es prudente porque contiene funciones distintas y datos locales.

Esta edición usa **identificador distinto**, permitiendo instalarse junto a Pro Stadium V3.1 sin modificar su instalación ni sus datos. **No es una actualización de Pro Stadium.** NFL Data Center empieza con almacenamiento propio; el historial de Pro Stadium no se traslada automáticamente. No desinstalar Pro Stadium ni borrar sus datos.

Para compilar: `gradle :app:assembleDebug`; el workflow mantiene las condiciones de firma existentes.

Al instalar, se mostrarán **dos aplicaciones** en Android: `NFL Totals Lab` (anterior) y `NFL Data Center` (nueva).

Esta versión contiene el prototipo visual de Simcast, no una integración completa de eventos/animaciones con Pro Stadium.
