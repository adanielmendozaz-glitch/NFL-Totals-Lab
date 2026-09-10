package com.nfltotalslab.app.branding

import androidx.compose.ui.graphics.Color

data class TeamBrand(
    val primary: Color,
    val secondary: Color,
    val logoUrl: String?
)

private val BRANDS = mapOf(
    "ARI" to TeamBrand(Color(0xFF97233F), Color(0xFFFFB612), "https://a.espncdn.com/i/teamlogos/nfl/500/ari.png"),
    "ATL" to TeamBrand(Color(0xFFA71930), Color(0xFF000000), "https://a.espncdn.com/i/teamlogos/nfl/500/atl.png"),
    "BAL" to TeamBrand(Color(0xFF241773), Color(0xFF9E7C0C), "https://a.espncdn.com/i/teamlogos/nfl/500/bal.png"),
    "BUF" to TeamBrand(Color(0xFF00338D), Color(0xFFC60C30), "https://a.espncdn.com/i/teamlogos/nfl/500/buf.png"),
    "CAR" to TeamBrand(Color(0xFF0085CA), Color(0xFF101820), "https://a.espncdn.com/i/teamlogos/nfl/500/car.png"),
    "CHI" to TeamBrand(Color(0xFF0B162A), Color(0xFFC83803), "https://a.espncdn.com/i/teamlogos/nfl/500/chi.png"),
    "CIN" to TeamBrand(Color(0xFFFB4F14), Color(0xFF000000), "https://a.espncdn.com/i/teamlogos/nfl/500/cin.png"),
    "CLE" to TeamBrand(Color(0xFF311D00), Color(0xFFFF3C00), "https://a.espncdn.com/i/teamlogos/nfl/500/cle.png"),
    "DAL" to TeamBrand(Color(0xFF041E42), Color(0xFF869397), "https://a.espncdn.com/i/teamlogos/nfl/500/dal.png"),
    "DEN" to TeamBrand(Color(0xFFFB4F14), Color(0xFF002244), "https://a.espncdn.com/i/teamlogos/nfl/500/den.png"),
    "DET" to TeamBrand(Color(0xFF0076B6), Color(0xFFB0B7BC), "https://a.espncdn.com/i/teamlogos/nfl/500/det.png"),
    "GB"  to TeamBrand(Color(0xFF203731), Color(0xFFFFB612), "https://a.espncdn.com/i/teamlogos/nfl/500/gb.png"),
    "HOU" to TeamBrand(Color(0xFF03202F), Color(0xFFA71930), "https://a.espncdn.com/i/teamlogos/nfl/500/hou.png"),
    "IND" to TeamBrand(Color(0xFF002C5F), Color(0xFFA2AAAD), "https://a.espncdn.com/i/teamlogos/nfl/500/ind.png"),
    "JAX" to TeamBrand(Color(0xFF006778), Color(0xFFD7A22A), "https://a.espncdn.com/i/teamlogos/nfl/500/jax.png"),
    "KC"  to TeamBrand(Color(0xFFE31837), Color(0xFFFFB81C), "https://a.espncdn.com/i/teamlogos/nfl/500/kc.png"),
    "LV"  to TeamBrand(Color(0xFF000000), Color(0xFFA5ACAF), "https://a.espncdn.com/i/teamlogos/nfl/500/lv.png"),
    "LAC" to TeamBrand(Color(0xFF0080C6), Color(0xFFFFC20E), "https://a.espncdn.com/i/teamlogos/nfl/500/lac.png"),
    "LA"  to TeamBrand(Color(0xFF003594), Color(0xFFFFA300), "https://a.espncdn.com/i/teamlogos/nfl/500/lar.png"),
    "MIA" to TeamBrand(Color(0xFF008E97), Color(0xFFFC4C02), "https://a.espncdn.com/i/teamlogos/nfl/500/mia.png"),
    "MIN" to TeamBrand(Color(0xFF4F2683), Color(0xFFFFC62F), "https://a.espncdn.com/i/teamlogos/nfl/500/min.png"),
    "NE"  to TeamBrand(Color(0xFF002244), Color(0xFFC60C30), "https://a.espncdn.com/i/teamlogos/nfl/500/ne.png"),
    "NO"  to TeamBrand(Color(0xFFD3BC8D), Color(0xFF101820), "https://a.espncdn.com/i/teamlogos/nfl/500/no.png"),
    "NYG" to TeamBrand(Color(0xFF0B2265), Color(0xFFA71930), "https://a.espncdn.com/i/teamlogos/nfl/500/nyg.png"),
    "NYJ" to TeamBrand(Color(0xFF125740), Color(0xFFFFFFFF), "https://a.espncdn.com/i/teamlogos/nfl/500/nyj.png"),
    "PHI" to TeamBrand(Color(0xFF004C54), Color(0xFFA5ACAF), "https://a.espncdn.com/i/teamlogos/nfl/500/phi.png"),
    "PIT" to TeamBrand(Color(0xFFFFB612), Color(0xFF101820), "https://a.espncdn.com/i/teamlogos/nfl/500/pit.png"),
    "SEA" to TeamBrand(Color(0xFF002244), Color(0xFF69BE28), "https://a.espncdn.com/i/teamlogos/nfl/500/sea.png"),
    "SF"  to TeamBrand(Color(0xFFAA0000), Color(0xFFB3995D), "https://a.espncdn.com/i/teamlogos/nfl/500/sf.png"),
    "TB"  to TeamBrand(Color(0xFFD50A0A), Color(0xFFFF7900), "https://a.espncdn.com/i/teamlogos/nfl/500/tb.png"),
    "TEN" to TeamBrand(Color(0xFF0C2340), Color(0xFF4B92DB), "https://a.espncdn.com/i/teamlogos/nfl/500/ten.png"),
    "WAS" to TeamBrand(Color(0xFF5A1414), Color(0xFFFFB612), "https://a.espncdn.com/i/teamlogos/nfl/500/wsh.png")
)

private val FALLBACK = TeamBrand(
    primary = Color(0xFF444444),
    secondary = Color(0xFF9E9E9E),
    logoUrl = null
)

fun teamBrand(team:String): TeamBrand = BRANDS[normalizeTeam(team)] ?: FALLBACK

fun teamKey(team:String): String = normalizeTeam(team)

private fun normalizeTeam(team:String): String = when(team.uppercase()){
    "LAR" -> "LA"
    "WSH" -> "WAS"
    else -> team.uppercase()
}
