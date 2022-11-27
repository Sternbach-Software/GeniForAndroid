package app.familygem

import android.content.Context
import android.widget.LinearLayout
import app.familygem.GedcomDateConverter
import android.widget.EditText
import app.familygem.R
import android.widget.TextView
import android.widget.NumberPicker
import android.widget.CompoundButton
import android.view.View.OnFocusChangeListener
import android.text.InputType
import android.view.View.OnTouchListener
import android.view.MotionEvent
import android.text.TextWatcher
import android.text.Editable
import android.util.AttributeSet
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.NumberPicker.OnValueChangeListener
import android.widget.CheckBox
import androidx.appcompat.widget.PopupMenu
import app.familygem.constant.Format
import app.familygem.constant.Kind
import java.util.*

class PublisherDateLinearLayout(context: Context?, `as`: AttributeSet?) :
    LinearLayout(context, `as`) {
    var gedcomDateConverter: GedcomDateConverter? = null
    var data1: GedcomDateConverter.Data? = null
    var data2: GedcomDateConverter.Data? = null
    var editText: EditText? = null
    var daysWheel = arrayOf(
        "-",
        "1",
        "2",
        "3",
        "4",
        "5",
        "6",
        "7",
        "8",
        "9",
        "10",
        "11",
        "12",
        "13",
        "14",
        "15",
        "16",
        "17",
        "18",
        "19",
        "20",
        "21",
        "22",
        "23",
        "24",
        "25",
        "26",
        "27",
        "28",
        "29",
        "30",
        "31"
    )
    var monthsWheel = arrayOf(
        "-",
        s(R.string.january),
        s(R.string.february),
        s(R.string.march),
        s(R.string.april),
        s(R.string.may),
        s(R.string.june),
        s(R.string.july),
        s(R.string.august),
        s(R.string.september),
        s(R.string.october),
        s(R.string.november),
        s(R.string.december)
    )
    var yearsWheel = arrayOfNulls<String>(101)
    var dateKinds = intArrayOf(
        R.string.exact, R.string.approximate, R.string.calculated, R.string.estimated,
        R.string.after, R.string.before, R.string.between_and,
        R.string.from, R.string.to, R.string.from_to, R.string.date_phrase
    )
    var calendar = GregorianCalendar.getInstance()
    var userIsTyping // determines if the user is actually typing on the virtual keyboard or if the text is changed in some other way	InputMethodManager tastiera;
            = false
    var keyboard: InputMethodManager? = null
    var keyboardIsVisible = false

    /**
     * Actions to be done only once at the beginning
     */
    fun initialize(editText: EditText) {
        addView(inflate(context, R.layout.editore_data, null), this.layoutParams)
        this.editText = editText
        for (i in 0 until yearsWheel.size - 1) yearsWheel[i] = if (i < 10) "0$i" else "" + i
        yearsWheel[100] = "-"
        gedcomDateConverter = GedcomDateConverter(editText.text.toString())
        data1 = gedcomDateConverter!!.data1
        data2 = gedcomDateConverter!!.data2

        // Setup the date editor
        if (Global.settings!!.expert) {
            val listTypes = findViewById<TextView>(R.id.editadata_tipi)
            listTypes.setOnClickListener { vista: View? ->
                val popup = PopupMenu(
                    context, vista!!
                )
                val menu = popup.menu
                for (i in 0 until dateKinds.size - 1) menu.add(0, i, 0, dateKinds[i])
                popup.show()
                popup.setOnMenuItemClickListener { item: MenuItem ->
                    gedcomDateConverter!!.kind = Kind.values()[item.itemId]
                    // If possibly invisible
                    findViewById<View>(R.id.editadata_prima).visibility = VISIBLE
                    if (data1!!.date == null) // wagon micro setting (??)
                        (findViewById<View>(R.id.prima_anno) as NumberPicker).value = 100
                    if (gedcomDateConverter!!.kind === Kind.BETWEEN_AND || gedcomDateConverter!!.kind === Kind.FROM_TO) {
                        findViewById<View>(R.id.editadata_seconda_avanzate).visibility = VISIBLE
                        findViewById<View>(R.id.editadata_seconda).visibility = VISIBLE
                        if (data2!!.date == null) (findViewById<View>(R.id.seconda_anno) as NumberPicker).value =
                            100
                    } else {
                        findViewById<View>(R.id.editadata_seconda_avanzate).visibility = GONE
                        findViewById<View>(R.id.editadata_seconda).visibility = GONE
                    }
                    listTypes.setText(dateKinds[item.itemId])
                    userIsTyping = false
                    generate()
                    true
                }
            }
            findViewById<View>(R.id.editadata_negativa1).setOnClickListener { vista: View ->
                data1!!.negative = (vista as CompoundButton).isChecked
                userIsTyping = false
                generate()
            }
            findViewById<View>(R.id.editadata_doppia1).setOnClickListener { vista: View ->
                data1!!.doubleYear = (vista as CompoundButton).isChecked
                userIsTyping = false
                generate()
            }
            findViewById<View>(R.id.editadata_negativa2).setOnClickListener { vista: View ->
                data2!!.negative = (vista as CompoundButton).isChecked
                userIsTyping = false
                generate()
            }
            findViewById<View>(R.id.editadata_doppia2).setOnClickListener { vista: View ->
                data2!!.doubleYear = (vista as CompoundButton).isChecked
                userIsTyping = false
                generate()
            }
            findViewById<View>(R.id.editadata_circa).visibility = GONE
        } else {
            findViewById<View>(R.id.editadata_circa).setOnClickListener { vista: View ->
                findViewById<View>(R.id.editadata_seconda).visibility =
                    GONE // casomai fosse visibile per tipi 6 o 9
                gedcomDateConverter!!.kind =
                    if ((vista as CompoundButton).isChecked) Kind.APPROXIMATE else Kind.EXACT
                userIsTyping = false
                generate()
            }
            findViewById<View>(R.id.editadata_avanzate).visibility = GONE
        }
        setupWagon(
            1, findViewById(R.id.prima_giorno), findViewById(R.id.prima_mese),
            findViewById(R.id.prima_secolo), findViewById(R.id.prima_anno)
        )
        setupWagon(
            2, findViewById(R.id.seconda_giorno), findViewById(R.id.seconda_mese),
            findViewById(R.id.seconda_secolo), findViewById(R.id.seconda_anno)
        )

        // At first focus it shows itself (EditoreData) hiding the keyboard
        keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        editText.onFocusChangeListener = OnFocusChangeListener { v: View?, hasFocus: Boolean ->
            if (hasFocus) {
                if (gedcomDateConverter!!.kind === Kind.PHRASE) {
                    //genera(); // Remove the parentheses from the sentence
                    editText.setText(gedcomDateConverter!!.phrase)
                } else {
                    keyboardIsVisible = keyboard!!.hideSoftInputFromWindow(
                        editText.windowToken,
                        0
                    ) // ok hide keyboard
                    /*Window window = ((Activity)getContext()).getWindow(); it doesn't help that the keyboard disappears
					window.setSoftInputMode( WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN );*/editText.inputType =
                        InputType.TYPE_NULL // disable keyboard text input
                    //needed in recent versions of android where the keyboard reappears
                }
                gedcomDateConverter!!.data1.date = null // a reset
                setAll()
                visibility = VISIBLE
            } else visibility = GONE
        }

        // The second touch brings up the keyboard
        editText.setOnTouchListener { view: View?, event: MotionEvent ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                editText.inputType = InputType.TYPE_CLASS_TEXT // re-enable the input
            } else if (event.action == MotionEvent.ACTION_UP) {
                keyboardIsVisible =
                    keyboard!!.showSoftInput(editText, 0) // makes the keyboard reappear
                //userIsTyping = true;
                //view.performClick(); non ne vedo l'utilità
            }
            false
        }
        // Set the date publisher based on what is written
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(testo: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun onTextChanged(testo: CharSequence, i: Int, i1: Int, i2: Int) {}
            override fun afterTextChanged(testo: Editable) {
                // i don't know why but in android 5 on first edition it is called 2 times which is not a problem anyway
                if (userIsTyping) setAll()
                userIsTyping = true
            }
        })
    }

    /**
     * Prepare the four wheels of a wagon with the initial settings
     */
    fun setupWagon(
        which: Int,
        dayWheel: NumberPicker,
        monthWheel: NumberPicker,
        centuryWheel: NumberPicker,
        yearWheel: NumberPicker
    ) {
        dayWheel.minValue = 0
        dayWheel.maxValue = 31
        dayWheel.displayedValues = daysWheel
        stylize(dayWheel)
        dayWheel.setOnValueChangedListener { picker: NumberPicker?, vecchio: Int, nuovo: Int ->
            update(
                if (which == 1) data1 else data2,
                dayWheel,
                monthWheel,
                centuryWheel,
                yearWheel
            )
        }
        monthWheel.minValue = 0
        monthWheel.maxValue = 12
        monthWheel.displayedValues = monthsWheel
        stylize(monthWheel)
        monthWheel.setOnValueChangedListener { picker: NumberPicker?, vecchio: Int, nuovo: Int ->
            update(
                if (which == 1) data1 else data2,
                dayWheel,
                monthWheel,
                centuryWheel,
                yearWheel
            )
        }
        centuryWheel.minValue = 0
        centuryWheel.maxValue = 20
        stylize(centuryWheel)
        centuryWheel.setOnValueChangedListener { picker: NumberPicker?, vecchio: Int, nuovo: Int ->
            update(
                if (which == 1) data1 else data2,
                dayWheel,
                monthWheel,
                centuryWheel,
                yearWheel
            )
        }
        yearWheel.minValue = 0
        yearWheel.maxValue = 100
        yearWheel.displayedValues = yearsWheel
        stylize(yearWheel)
        yearWheel.setOnValueChangedListener { picker: NumberPicker?, vecchio: Int, nuovo: Int ->
            update(
                if (which == 1) data1 else data2,
                dayWheel,
                monthWheel,
                centuryWheel,
                yearWheel
            )
        }
    }

    fun stylize(wheel: NumberPicker) {
        wheel.isSaveFromParentEnabled = false
    }

    /**
     * Take the date string, update the Dates, and edit all of it in the date editor
     * Called when I click on the editable field, and after each text edit
     */
    fun setAll() {
        gedcomDateConverter!!.analyze(editText!!.text.toString())
        (findViewById<View>(R.id.editadata_circa) as CheckBox).isChecked =
            gedcomDateConverter!!.kind === Kind.APPROXIMATE
        (findViewById<View>(R.id.editadata_tipi) as TextView).setText(dateKinds[gedcomDateConverter!!.kind!!.ordinal])

        // First wagon
        setWagon(
            data1, findViewById(R.id.prima_giorno), findViewById(R.id.prima_mese),
            findViewById(R.id.prima_secolo), findViewById(R.id.prima_anno)
        )
        if (Global.settings!!.expert) setCheckboxes(data1)

        // Second wagon
        if (gedcomDateConverter!!.kind === Kind.BETWEEN_AND || gedcomDateConverter!!.kind === Kind.FROM_TO) {
            setWagon(
                data2, findViewById(R.id.seconda_giorno), findViewById(R.id.seconda_mese),
                findViewById(R.id.seconda_secolo), findViewById(R.id.seconda_anno)
            )
            if (Global.settings!!.expert) {
                findViewById<View>(R.id.editadata_seconda_avanzate).visibility = VISIBLE
                setCheckboxes(data2)
            }
            findViewById<View>(R.id.editadata_seconda).visibility = VISIBLE
        } else {
            findViewById<View>(R.id.editadata_seconda_avanzate).visibility = GONE
            findViewById<View>(R.id.editadata_seconda).visibility = GONE
        }
    }

    /**
     * Spin the wheels of a wagon according to a date
     */
    fun setWagon(
        data: GedcomDateConverter.Data?,
        dayPicker: NumberPicker,
        monthPicker: NumberPicker,
        centuryPicker: NumberPicker,
        yearPicker: NumberPicker
    ) {
        calendar.clear()
        if (data!!.date != null) calendar.time = data.date
        dayPicker.maxValue = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (data.date != null && (data.isFormat(Format.D_M_Y) || data.isFormat(Format.D_M))) dayPicker.value =
            data.date!!.date else dayPicker.value = 0
        if (data.date == null || data.isFormat(Format.Y)) monthPicker.value =
            0 else monthPicker.value = data.date!!.month + 1
        if (data.date == null || data.isFormat(Format.D_M)) centuryPicker.value =
            0 else centuryPicker.value = (data.date!!.year + 1900) / 100
        if (data.date == null || data.isFormat(Format.D_M)) yearPicker.value =
            100 else yearPicker.value = (data.date!!.year + 1900) % 100
    }

    /**
     * Set the Checkboxes for a date which can be negative and double
     */
    fun setCheckboxes(data: GedcomDateConverter.Data?) {
        val checkboxBC: CheckBox
        val checkboxDouble: CheckBox
        if (data == data1) {
            checkboxBC = findViewById(R.id.editadata_negativa1)
            checkboxDouble = findViewById(R.id.editadata_doppia1)
        } else {
            checkboxBC = findViewById(R.id.editadata_negativa2)
            checkboxDouble = findViewById(R.id.editadata_doppia2)
        }
        if (data!!.date == null || data.isFormat(Format.EMPTY) || data.isFormat(Format.D_M)) { // dates without year
            checkboxBC.visibility = INVISIBLE
            checkboxDouble.visibility = INVISIBLE
        } else {
            checkboxBC.isChecked = data.negative
            checkboxBC.visibility = VISIBLE
            checkboxDouble.isChecked = data.doubleYear
            checkboxDouble.visibility = VISIBLE
        }
    }

    /**
     * Update a Date with the new values taken from the wheels
     */
    fun update(
        data: GedcomDateConverter.Data?,
        dayPicker: NumberPicker,
        monthPicker: NumberPicker,
        centuryPicker: NumberPicker,
        yearPicker: NumberPicker
    ) {
        if (keyboardIsVisible) {    // Hides any visible keyboard
            keyboardIsVisible = keyboard!!.hideSoftInputFromWindow(editText!!.windowToken, 0)
            // Hides the keyboard right away, but needs a second try to return false. It's not a problem anyway
        }
        val day = dayPicker.value
        val month = monthPicker.value
        val century = centuryPicker.value
        val year = yearPicker.value
        // Set the days of the month in dayWheel
        calendar[century * 100 + year, month - 1] = 1
        dayPicker.maxValue = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (data!!.date == null) data.date = Date()
        data.date!!.date = if (day == 0) 1 else day // otherwise the M_A date goes back one month
        data.date!!.month = if (month == 0) 0 else month - 1
        data.date!!.year = if (year == 100) -1899 else century * 100 + year - 1900
        if (day != 0 && month != 0 && year != 100) data.format.applyPattern(Format.D_M_Y) else if (day != 0 && month != 0) data.format.applyPattern(
            Format.D_M
        ) else if (month != 0 && year != 100) data.format.applyPattern(Format.MMM_Y) else if (year != 100) data.format.applyPattern(
            Format.Y
        ) else data.format.applyPattern(Format.EMPTY)
        setCheckboxes(data)
        userIsTyping = false
        generate()
    }

    /**
     * Rebuilds the string with the end date and puts it in [.editText]
     */
    fun generate() {
        val redone: String
        if (gedcomDateConverter!!.kind === Kind.EXACT) redone =
            redo(data1) else if (gedcomDateConverter!!.kind === Kind.BETWEEN_AND) redone =
            "BET " + redo(data1) + " AND " + redo(data2) else if (gedcomDateConverter!!.kind === Kind.FROM_TO) redone =
            "FROM " + redo(data1) + " TO " + redo(data2) else if (gedcomDateConverter!!.kind === Kind.PHRASE) {
            // The phrase is replaced by the exact date
            gedcomDateConverter!!.kind = Kind.EXACT
            (findViewById<View>(R.id.editadata_tipi) as TextView).setText(dateKinds[0])
            redone = redo(data1)
        } else redone = gedcomDateConverter!!.kind!!.prefix + " " + redo(data1)
        editText!!.setText(redone)
    }

    /**
     * Writes the single date according to the format
     */
    fun redo(data: GedcomDateConverter.Data?): String {
        var done = ""
        if (data!!.date != null) {
            // Dates with double year
            if (data.doubleYear && !(data.isFormat(Format.EMPTY) || data.isFormat(Format.D_M))) {
                val aYearLater = Date()
                aYearLater.year = data.date!!.year + 1
                val secondoAnno = String.format(Locale.ENGLISH, "%tY", aYearLater)
                done = data.format.format(data.date) + "/" + secondoAnno.substring(2)
            } else  // The other normal dates
                done = data.format.format(data.date)
        }
        if (data.negative) done += " B.C."
        return done
    }

    /**
     * Called from outside essentially just to add parentheses to the given sentence
     */
    fun encloseInParentheses() {
        if (gedcomDateConverter!!.kind === Kind.PHRASE) {
            editText!!.setText("(" + editText!!.text + ")")
        }
    }

    fun s(id: Int): String {
        return Global.context!!.getString(id)
    }
}