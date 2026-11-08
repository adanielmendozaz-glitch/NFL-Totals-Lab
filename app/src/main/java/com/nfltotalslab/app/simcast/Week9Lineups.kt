package com.nfltotalslab.app.simcast

import com.nfltotalslab.app.data.GameRecord

/** Official NFL Week 9 game-designation snapshot captured 2026-11-08 08:10 CST.
 * NOT a live injury feed. Recheck the official 90-minute inactives before kickoff.
 * QB expected vs club-confirmed are distinct. Uncertain players remain eligible.
 */
internal data class Week9TeamStatus(
    val qb:String, val qbAnnounced:Boolean,
    val out:List<String>, val ir:List<String>,
    val questionable:List<String>, val doubtful:List<String>,
    val qbSource:String
) {
    fun lineup():TeamLineup = TeamLineup(
        startingQb=qb,
        outNames=(out+ir).toSet(),
        source="NFL W9 2026-11-08 08:10 CST; QB " +
            (if(qbAnnounced) "anunciado" else "previsto, pendiente de confirmar")+
            "; lesiones NFL.com; QB: " + qbSource,
        verified=qbAnnounced
    )
}
internal data class Week9GameStatus(
    val away:String, val home:String, val awayStatus:Week9TeamStatus,
    val homeStatus:Week9TeamStatus, val neutralSite:Boolean=false
)

/** Static week-9 patch; it is intentionally restricted to 2026 regular season W9. */
object Week9Lineups {
    const val AS_OF="2026-11-08 08:10 CST"
    const val NFL_INJURIES="https://www.nfl.com/news/nfl-week-9-injury-report-2026-season"
    private val fixtures=listOf(
        Week9GameStatus("CIN", "ATL",
            Week9TeamStatus("Joe Burrow", false, listOf("Kyle Dugger"), listOf("Tanner Hudson"), listOf("Cedric Johnson"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Michael Penix Jr.", false, listOf("Brian Robinson Jr.", "Zachariah Branch", "Cameron Williams", "Billy Bowman"), emptyList(), listOf("Beaux Collins", "James Pearce Jr.", "Yasir Abdullah", "Divine Deablo"), emptyList(), "Depth chart semana 9"), neutralSite=true),
        Week9GameStatus("DEN", "CAR",
            Week9TeamStatus("Bo Nix", false, listOf("Eyioma Uwazurike", "Kris Abrams-Draine", "Brandon Jones"), emptyList(), listOf("Pat Bryant", "Dondrea Tillman", "Talanoa Hufanga"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Bryce Young", false, listOf("Damien Lewis", "Jaelan Phillips", "Bobby Okereke", "Will Lee III"), emptyList(), listOf("Corey Bullock", "Brady Christensen", "Torricelli Simpkins III"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("DAL", "IND",
            Week9TeamStatus("Dak Prescott", false, listOf("KaVontae Turpin", "Malachi Lawrence"), emptyList(), listOf("George Pickens", "Jake Ferguson", "Luke Schoonmaker", "Jonathan Bullard", "Marist Liufau", "DeMarvion Overshown", "Joey Porter Jr."), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Daniel Jones", false, listOf("Akeem Davis-Gaither", "Justin Walley", "A.J. Haulcy"), emptyList(), listOf("Jalen Travis"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("NYJ", "KC",
            Week9TeamStatus("Geno Smith", false, listOf("Garrett Wilson", "Joseph Ossai", "Trevin Wallace", "Nahshon Wright", "VJ Payne"), emptyList(), listOf("Harrison Phillips"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Patrick Mahomes", false, listOf("Omarr Norman-Lott", "Peter Woods", "Mansoor Delane"), emptyList(), listOf("Nikko Remigio"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("DET", "MIA",
            Week9TeamStatus("Jared Goff", false, listOf("Cade Mays", "D.J. Wonnum", "Jack Campbell", "D.J. Reed", "Kerby Joseph"), emptyList(), listOf("Jacob Saylors", "Sione Vaki", "Amon-Ra St. Brown"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Malik Willis", false, emptyList(), emptyList(), listOf("Kaytron Allen", "Jaylen Wright", "Malik Herring"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("CLE", "NO",
            Week9TeamStatus("Shedeur Sanders", true, listOf("Maliek Collins"), emptyList(), listOf("Deshaun Watson"), emptyList(), "https://www.clevelandbrowns.com/news/shedeur-sanders-to-start-week-9-against-the-saints"),
            Week9TeamStatus("Tyler Shough", false, listOf("CJ Donaldson"), emptyList(), listOf("Jordyn Tyson", "Kelvin Banks Jr.", "Asim Richards", "Martin Emerson"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("NYG", "PHI",
            Week9TeamStatus("J.J. McCarthy", false, emptyList(), emptyList(), listOf("Charlie Jones"), listOf("Cam Skattebo"), "Depth chart semana 9"),
            Week9TeamStatus("Joe Milton III", true, listOf("Britain Covey", "DeVonta Smith", "Dontayvion Wicks", "Jihaad Campbell", "Jonathan Jones", "Michael Carter II"), listOf("Jalen Hurts"), listOf("Tanner McKee"), emptyList(), "https://www.nfl.com/news/eagles-starting-qb-joe-milton-tanner-mckee-week-9-vs-giants"), neutralSite=false),
        Week9GameStatus("LAR", "WAS",
            Week9TeamStatus("Matthew Stafford", false, listOf("Terrance Ferguson", "Jaylen Watson"), emptyList(), listOf("Jordan Whittington", "Colby Parkinson", "Grant Stuard", "Trent McDuffie", "Kamren Curl"), listOf("Joe Cardona"), "Depth chart semana 9"),
            Week9TeamStatus("Jayden Daniels", false, listOf("Dyami Brown", "Treylon Burks"), emptyList(), emptyList(), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("HOU", "LAC",
            Week9TeamStatus("C.J. Stroud", false, listOf("David Montgomery", "Reed Blankenship"), emptyList(), emptyList(), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Justin Herbert", false, listOf("Deane Leonard", "Kendall Williamson"), emptyList(), listOf("Brenen Thompson", "David Njoku", "Kayode Awosika", "Denzel Perryman"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("LV", "SF",
            Week9TeamStatus("Kirk Cousins", false, listOf("Thomas Booker"), emptyList(), listOf("Darien Porter"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Brock Purdy", false, listOf("Mike Evans", "Christian Kirk", "Deebo Samuel"), emptyList(), listOf("Upton Stout", "Nate Hobbs", "Siran Neal"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("GB", "NE",
            Week9TeamStatus("Jordan Love", false, listOf("Josh Whyle", "Travis Glover", "Sean Rhyan", "Micah Parsons", "Zaire Franklin"), emptyList(), listOf("Chris Brooks", "MarShawn Lloyd", "Keisean Nixon"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Drake Maye", false, listOf("TreVeyon Henderson", "A.J. Brown"), emptyList(), listOf("Mack Hollins", "Hunter Henry", "Morgan Moses", "Greg Van Roten", "Carlton Davis", "Kevin Byard", "Brenden Schooler"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("AZ", "SEA",
            Week9TeamStatus("Gardner Minshew II", false, listOf("Bam Knight", "Jon Gaines II", "Jayden Williams"), emptyList(), listOf("Isaiah Adams", "Walter Nolen", "Max Melton"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Drew Lock", true, listOf("Sam Darnold", "Rashid Shaheed", "Charles Cross", "Quenton Nelson", "Brandon Pili", "Leonard Williams", "Uchenna Nwosu"), emptyList(), listOf("Connor O'Toole"), emptyList(), "https://www.seahawks.com/news/seahawks-qb-sam-darnold-out-for-sunday-s-game-vs-arizona-due-to-quad-injury"), neutralSite=false),
        Week9GameStatus("TB", "CHI",
            Week9TeamStatus("Baker Mayfield", false, listOf("YaYa Diaby"), emptyList(), listOf("Emeka Egbuka"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Caleb Williams", false, emptyList(), emptyList(), listOf("Kyle Monangai", "Malik Muhammad", "Tyrique Stevenson"), emptyList(), "Depth chart semana 9"), neutralSite=false),
        Week9GameStatus("BUF", "MIN",
            Week9TeamStatus("Josh Allen", false, listOf("Josh Palmer"), emptyList(), listOf("Tyrell Shavers", "Alec Anderson", "Dion Dawkins", "Greg Gaines", "Jordan Hancock"), emptyList(), "Depth chart semana 9"),
            Week9TeamStatus("Carson Wentz", true, listOf("Kyler Murray", "Demond Claiborne", "Jauan Jennings"), emptyList(), listOf("Theo Jackson", "Jay Ward"), emptyList(), "https://www.nfl.com/news/vikings-to-start-carson-wentz-vs-bills-kyler-murray-hamstring"), neutralSite=false)
    )
    internal fun details(game:GameRecord):Week9GameStatus? =
        if(game.season==2026 && game.week==9 && game.gameType=="REG")
            fixtures.firstOrNull { it.away==game.awayTeam && it.home==game.homeTeam }
        else null
    fun forGame(game:GameRecord):GameLineup? = details(game)?.let {
        GameLineup(it.awayStatus.lineup(),it.homeStatus.lineup())
    }
}
