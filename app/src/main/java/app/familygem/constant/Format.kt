package app.familygem.constant

/**
 * All of the formats a Date can be displayed as
 */
object Format {
    const val D_M_Y = "d MMM yyy"
    const val D_m_Y = "d M yyy"
    const val MMM_Y = "MMM yyy"
    const val M_Y = "M yyy"
    const val D_M = "d MMM"
    const val Y = "yyy"
    const val EMPTY = ""

    val PATTERNS = listOf(
        D_M_Y,
        D_m_Y,
        MMM_Y,
        M_Y,
        D_M,
        Y,
        EMPTY,
    )
}