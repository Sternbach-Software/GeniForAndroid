package app.familygem

import app.familygem.GedcomDateConverter
import app.familygem.U
import app.familygem.R
import app.familygem.constant.Format
import app.familygem.constant.Kind
import java.lang.Exception
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*

/**
 * This class receives a Gedcom date, parses it and translates it into a Data class
 */
class GedcomDateConverter {
    var data1: Data
    var data2: Data? = null
    var phrase //The one that will go in parentheses // Quella che andrà tra parentesi
            : String? = null
    var kind: Kind? = null

    /**
     * With a string date in GEDCOM style
     */
    constructor(gedcomDate: String) {
        data1 = Data()
        data2 = Data()
        analyze(gedcomDate)
    }

    /**
     * With one single complete Date
     */
    constructor(date: Date?) {
        data1 = Data()
        data1.date = date
        data1.format.applyPattern(Format.D_M_Y)
        kind = Kind.EXACT
    }

    inner class Data internal constructor() {
        var date: Date? = null
        var format: SimpleDateFormat
        var negative = false
        var doubleYear = false

        init {
            val simboliFormato = DateFormatSymbols()
            simboliFormato.shortMonths = gedcomMonths
            format = SimpleDateFormat()
            format.dateFormatSymbols = simboliFormato
        }

        /**
         * It takes an exact Gedcom date and stuffs the attributes of the Date class into it
         * // Prende una data Gedcom esatta e ci farcisce gli attributi della classe Data
         */
        fun scan(dataGc: String) {

            // Recognize if the date is B.C. and remove the suffix
            var dataGc = dataGc
            negative = false //eventually reset to true // resetta eventuale true
            for (suffix in suffixes) {
                if (dataGc.endsWith(suffix)) {
                    negative = true
                    dataGc = dataGc.substring(0, dataGc.indexOf(suffix)).trim { it <= ' ' }
                    break
                }
            }
            dataGc = dataGc.replace(
                "[\\\\_\\-|.,;:?'\"#^&*°+=~()\\[\\]{}]".toRegex(),
                " "
            ) // tutti tranne '/'

            // Distinguishes a date with a double year 1712/1713 from a date type 17/12/1713
            doubleYear = false // reset
            if (dataGc.indexOf('/') > 0) {
                val tata = dataGc.split("[/ ]").toTypedArray()
                if (tata.size > 1 && tata[tata.size - 2].length < 3 && U.extractNum(tata[tata.size - 2]) <= 12) dataGc =
                    dataGc.replace('/', ' ') else doubleYear = true
            }
            for (dateFormat in Format.PATTERNS) {
                format.applyPattern(dateFormat)
                try {
                    date = format.parse(dataGc)
                    break
                } catch (e: ParseException) {
                }
            }
            if (isFormat(Format.D_m_Y)) format.applyPattern(Format.D_M_Y)
            if (isFormat(Format.M_Y)) format.applyPattern(Format.MMM_Y)

            // Makes date effectively negative (for age calculation)
            if (negative) changeEra()
        }

        /**
         * Makes the date BC or AD consistent with the 'negative' boolean
         */
        fun changeEra() {
            if (date != null) {
                // The date is repaired by changing the era
                val sdf = SimpleDateFormat(Format.D_M_Y + " G", Locale.US)
                var data = sdf.format(date)
                data = if (negative) data.replace("AD", "BC") else data.replace("BC", "AD")
                try {
                    date = sdf.parse(data)
                } catch (e: Exception) {
                }
            }
        }

        fun isFormat(format: String): Boolean {
            return this.format.toPattern() == format
        }

        override fun toString(): String {
            val format: DateFormat = SimpleDateFormat("d MMM yyyy G HH:mm:ss", Locale.US)
            return format.format(date)
        }
    }

    /**
     * It recognizes the type of data and creates the Data class
     */
    fun analyze(dataGc: String) {

        // Reset the important values
        var dataGc = dataGc
        kind = null
        data1.date = null
        dataGc = dataGc.trim { it <= ' ' }
        if (dataGc.isEmpty()) {
            kind = Kind.EXACT
            return
        }
        // It recognizes types other than EXACT and converts the string to Data
        val dataGcMaiusc = dataGc.uppercase(Locale.getDefault())
        for (i in 1 until Kind.values().size) {
            val k = Kind.values()[i]
            if (dataGcMaiusc.startsWith(k.prefix)) {
                kind = k
                if (k === Kind.BETWEEN_AND && dataGcMaiusc.contains("AND")) {
                    if (dataGcMaiusc.indexOf("AND") > dataGcMaiusc.indexOf("BET") + 4) data1.scan(
                        dataGcMaiusc.substring(4, dataGcMaiusc.indexOf("AND") - 1)
                    )
                    if (dataGcMaiusc.length > dataGcMaiusc.indexOf("AND") + 3) data2!!.scan(
                        dataGcMaiusc.substring(dataGcMaiusc.indexOf("AND") + 4)
                    )
                } else if (k === Kind.FROM && dataGcMaiusc.contains("TO")) {
                    kind = Kind.FROM_TO
                    if (dataGcMaiusc.indexOf("TO") > dataGcMaiusc.indexOf("FROM") + 5) data1.scan(
                        dataGcMaiusc.substring(5, dataGcMaiusc.indexOf("TO") - 1)
                    )
                    if (dataGcMaiusc.length > dataGcMaiusc.indexOf("TO") + 2) data2!!.scan(
                        dataGcMaiusc.substring(dataGcMaiusc.indexOf("TO") + 3)
                    )
                } else if (k === Kind.PHRASE) { // Phrase date between parenthesis
                    phrase = if (dataGc.endsWith(")")) dataGc.substring(
                        1,
                        dataGc.indexOf(")")
                    ) else dataGc
                } else if (dataGcMaiusc.length > k.prefix.length) // Other prefixes followed by something
                    data1.scan(dataGcMaiusc.substring(k.prefix.length + 1))
                break
            }
        }
        //It remains to prove the type EXACT, otherwise it becomes a sentence // Rimane da provare il type EXACT, altrimenti diventa una frase
        if (kind == null) {
            data1.scan(dataGc)
            if (data1.date != null) {
                kind = Kind.EXACT
            } else {
                phrase = dataGc
                kind = Kind.PHRASE
            }
        }
    }

    /** Write a short text-version of the date in the default locale.
     * @param yearOnly Write the year only or the whole date with day and month
     * @return The date well written
     */
    fun writeDate(yearOnly: Boolean): String {
        var text = ""
        if (data1.date != null && !(data1.isFormat(Format.D_M) && yearOnly)) {
            val locale = Locale.getDefault()
            var dateFormat: DateFormat =
                SimpleDateFormat(if (yearOnly) Format.Y else data1.format.toPattern(), locale)
            val dateOne =
                data1.date!!.clone() as Date // Cloned so the year of a double date can be modified without consequences
            if (data1.doubleYear) dateOne.year = data1.date!!.year + 1
            text = dateFormat.format(dateOne)
            if (data1.negative) text = "-$text"
            if (kind === Kind.APPROXIMATE || kind === Kind.CALCULATED || kind === Kind.ESTIMATED) text += "?" else if (kind === Kind.AFTER || kind === Kind.FROM) text += "→" else if (kind === Kind.BEFORE) text =
                "←$text" else if (kind === Kind.TO) text =
                "→$text" else if ((kind === Kind.BETWEEN_AND || kind === Kind.FROM_TO) && data2!!.date != null) {
                val dateTwo = data2!!.date!!.clone() as Date
                if (data2!!.doubleYear) dateTwo.year = data2!!.date!!.year + 1
                dateFormat =
                    SimpleDateFormat(if (yearOnly) Format.Y else data2!!.format.toPattern(), locale)
                var second = dateFormat.format(dateTwo)
                if (data2!!.negative) second = "-$second"
                if (second != text) {
                    if (!data1.negative && !data2!!.negative) {
                        if (!yearOnly && data1.isFormat(Format.D_M_Y) && data1.format == data2!!.format && dateOne.month == dateTwo.month && dateOne.year == dateTwo.year) { // Same month and year
                            text = text.substring(0, text.indexOf(' '))
                        } else if (!yearOnly && data1.isFormat(Format.D_M_Y) && data1.format == data2!!.format && dateOne.year == dateTwo.year) { // Same year
                            text = text.substring(0, text.lastIndexOf(' '))
                        } else if (!yearOnly && data1.isFormat(Format.MMM_Y) && data1.format == data2!!.format && dateOne.year == dateTwo.year) { // Same year
                            text = text.substring(0, text.indexOf(' '))
                        } else if ((yearOnly || data1.isFormat(Format.Y) && data1.format == data2!!.format) // Two years only
                            && (text.length == 4 && second.length == 4 && text.substring(
                                0,
                                2
                            ) == second.substring(0, 2) // of the same century
                                    || text.length == 3 && second.length == 3 && text.substring(
                                0,
                                1
                            ) == second.substring(0, 1))
                        ) {
                            second =
                                second.substring(second.length - 2) // Keeps the last two digits
                        }
                    }
                    text += (if (kind === Kind.BETWEEN_AND) "~" else "→") + second
                }
            }
        }
        return text
    }

    /**
     * Plain text of the date in local language
     */
    fun writeDateLong(): String {
        var txt = ""
        val pre = when (kind) {
            Kind.APPROXIMATE -> R.string.approximate
            Kind.CALCULATED -> R.string.calculated
            Kind.ESTIMATED -> R.string.estimated
            Kind.AFTER -> R.string.after
            Kind.BEFORE -> R.string.before
            Kind.BETWEEN_AND -> R.string.between
            Kind.FROM, Kind.FROM_TO -> R.string.from
            Kind.TO -> R.string.to
            Kind.EXACT, Kind.PHRASE, null -> 0 //TODO why did he not cover these cases? I (@Sternbach-Software) added them
        }
        if (pre > 0) txt = Global.context.getString(pre)
        if (data1.date != null) {
            txt += writePiece(data1)
            // Uppercase initial
            if (kind === Kind.EXACT && data1.isFormat(Format.MMM_Y)) {
                txt = txt.first().uppercase(Locale.getDefault()) + txt.substring(1)
            }
            if (kind === Kind.BETWEEN_AND || kind === Kind.FROM_TO) {
                txt += " " + Global.context.getString(if (kind === Kind.BETWEEN_AND) R.string.and else R.string.to)
                    .lowercase(
                        Locale.getDefault()
                    )
                if (data2!!.date != null) txt += writePiece(data2)
            }
        } else if (phrase != null) {
            txt = phrase as String
        }
        return txt.trim { it <= ' ' }
    }

    fun writePiece(date: Data?): String {
        val dateFormat: DateFormat =
            SimpleDateFormat(date!!.format.toPattern().replace("MMM", "MMMM"), Locale.getDefault())
        var txt = " " + dateFormat.format(date.date)
        if (date.doubleYear) {
            val year = (date.date!!.year + 1901).toString()
            txt += if (year.length > 1) // Two or more digits
                "/" + year.substring(year.length - 2) else  // One digit
                "/0$year"
        }
        if (date.negative) txt += " B.C."
        return txt
    }

    /**
     * Return an integer representing the main date in the format YYYYMMDD, otherwise MAX_VALUE
     */
    val dateNumber: Int
        get() = if (data1.date != null && !data1.isFormat(Format.D_M)) {
            (data1.date!!.year + 1900) * 10000 + (data1.date!!.month + 1) * 100 + data1.date!!.date
        } else Int.MAX_VALUE

    /**
     * Kinds of date that represent a single event in time
     */
    val isSingleKind: Boolean
        get() = kind === Kind.EXACT || kind === Kind.APPROXIMATE || kind === Kind.CALCULATED || kind === Kind.ESTIMATED

    companion object {
        val gedcomMonths = arrayOf(
            "JAN",
            "FEB",
            "MAR",
            "APR",
            "MAY",
            "JUN",
            "JUL",
            "AUG",
            "SEP",
            "OCT",
            "NOV",
            "DEC"
        )
        val suffixes = arrayOf("B.C.", "BC", "BCE")
    }
}