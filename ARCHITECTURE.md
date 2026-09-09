# NFL Totals Lab — V0.2 Native

Jornada -> Analyze -> TotalsEngine -> SQLite -> Ranking/Censo
        \-> Sync -> nflverse Schedule + PBP + Rosters + Injuries
                    \-> TeamMetrics -> Markov/NegBin/DriveMC/Bayes/Shadow

Principio de diseño:
- La app NO depende del navegador.
- La predicción persiste antes de abandonar la pantalla.
- Los resultados finales liquidan el Censo en el siguiente sync.
- Ranking = última predicción por game_id.
- Censo = todas las predicciones históricas.
