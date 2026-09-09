# NFL Totals Lab V0.1

Primer núcleo funcional para totales NFL (Over/Under).

## Motores incluidos
- Markov Drive (30%)
- Negative Binomial / Gamma-Poisson (25%)
- Drive Monte Carlo (20%)
- Bayesian shrinkage (15%)
- Shadow Poisson (10%)

## Funciones
- Total de juego completo
- Línea O/U editable
- Hasta 500k simulaciones solicitadas, repartidas entre los 5 motores
- P(Over), P(Under), proyección y percentiles
- Clasificación:
  - JUGABLE ★ >= 66%
  - LEAN >= 60%
  - PASS < 60%
- Ranking persistente
- Censo persistente
- Data Vault JSON export/import
- Diseño móvil para Android

## Importante
V0.1 valida motor + persistencia. Los parámetros del equipo son editables y comienzan en valores neutrales/de ejemplo.
No interpretes un pick de V0.1 como apuesta basada en datos 2026 hasta conectar el feed NFL.

## Próxima versión
V0.2:
- ingestión automática nflverse / nflfastR
- EPA/play ataque y defensa
- success rate
- drives/juego
- points/drive
- red-zone TD rate
- explosive play rate
- rolling windows + Bayesian prior
- autoload de juegos
- clima/injuries como capas posteriores

## Instalación rápida Netlify
Sube el contenido de esta carpeta como sitio estático. `index.html` debe quedar en la raíz publicada.
