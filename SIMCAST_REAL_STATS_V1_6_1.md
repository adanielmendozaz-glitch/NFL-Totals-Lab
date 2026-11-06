# Simcast V1.6.1 — Statistical source calibration

## Real input rules

- **NFLverse Player Weekly:** last four prior weeks, weighted by recency; used for each player's usage and efficiency. Never reads the match week or later.
- **The Margin EPA:** used only if the PBP snapshot is absent or lacks 2 prior games, and the rating has a `through_week` earlier than the requested matchup. Its `as_of` must not fall on a later calendar date.
- **PBP TeamMetrics:** used when its count of games can be shown to be from completed earlier games; otherwise ignored for the Simcast.
- **NFLverse Team Season Stats / Player Season Totals:** CSV snapshots without a certified week cutoff are shown in Data Center but excluded from historical/pregame simulation. They may be used only after a future authenticated cutoff/temporal archive is implemented.

## What changed

- Matchup defensive EPA direction corrected (EPA allowed positive = defense allows more offensive gains).
- Bayesian weighted team passing share, completion rate, passing YPA and rushing YPC from prior weekly player data; player-specific posteriors combine with the team sample without overfitting to tiny samples.
- Fourth-down conversions now possible in field/clock-dependent situations; turnover on downs changes field position explicitly.
- Exports capture the exact EPA source and weekly cutoff and show reference matchup points-per-game from prior completed fixtures. Reference PPG is diagnostic, **not** a forced score.
- The official Core betting prediction is unchanged. The simcast remains experimental. A future out-of-sample walk-forward audit must validate any accuracy claim.

## Integrity and audit

Continue to check score/play reconciliation, period boundaries, field position and try events. JSON version `1.6.1-REAL-WEEKLY-CALIBRATION`. A 100/500/1000 batch is stochastic and does not itself establish predictive calibration.

- The Margin EPA splits (`off_pass_epa_play`, `def_pass_epa_play`, `off_rush_epa_play`, `def_rush_epa_play`) adjust pass and rush separately when available. Lower defensive EPA means a stronger defense.
- Passing completion likelihood is now conditioned on an attempted pass; a sack does not count as an attempt.
- Batch auditor flags an absolute deviation larger than 25% versus shrunk prior-week matchup PPG. This is a warning, not a force-to-target scoring override.
