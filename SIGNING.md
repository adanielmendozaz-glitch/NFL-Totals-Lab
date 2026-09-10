# NFL Totals Lab - Permanent Development Signing

Desde V0.4.2 los APK distribuidos por GitHub Actions usan una keystore privada
almacenada en GitHub Actions Secrets.

La clave privada NO está en este repositorio.

Alias: `nfltotalslab`

SHA-256 del certificado:

`CA:22:48:8F:B1:C4:F0:A8:24:F3:89:82:E3:86:2E:53:B8:4E:34:A4:57:C3:8C:36:4C:9A:5F:36:0B:46:08:95`

Secrets usados:
- `NFL_KEYSTORE_BASE64`
- `NFL_STORE_PASSWORD`
- `NFL_KEY_PASSWORD`
- `NFL_KEY_ALIAS`

El backup privado local se guarda en:

`$HOME/.nfl-totals-lab-signing/`

Regla: no borrar ni regenerar esa keystore si se desea conservar compatibilidad
de actualización entre APK V0.4.2+.
