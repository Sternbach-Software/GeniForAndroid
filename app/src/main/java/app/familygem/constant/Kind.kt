package app.familygem.constant

/**
 * GEDCOM date types
 */
enum class Kind(  // Todo "INT"
    val prefix: String
) {
    EXACT(""),
    APPROXIMATE("ABT"),
    CALCULATED("CAL"),
    ESTIMATED("EST"),
    AFTER("AFT"),
    BEFORE("BEF"),
    BETWEEN_AND("BET"),
    FROM("FROM"),
    TO("TO"),
    FROM_TO("FROM"),
    PHRASE("(")
}
/*
/**
 * GEDCOM date types
 */
@StringDef(
    EXACT,
    APPROXIMATE,
    CALCULATED,
    ESTIMATED,
    AFTER,
    BEFORE,
    BETWEEN_AND,
    FROM,
    TO,
    FROM_TO,
    PHRASE,
)
@Retention(AnnotationRetention.SOURCE)
annotation class Kind {

}

const val EXACT = ""
const val APPROXIMATE = "ABT"
const val CALCULATED = "CAL"
const val ESTIMATED = "EST"
const val AFTER = "AFT"
const val BEFORE = "BEF"
const val BETWEEN_AND = "BET"
const val FROM = "FROM"
const val TO = "TO"
const val FROM_TO = "FROM"
const val PHRASE = "("*/