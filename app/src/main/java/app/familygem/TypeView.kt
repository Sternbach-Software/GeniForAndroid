package app.familygem

import android.content.Context
import app.familygem.TypeView.Combo
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import app.familygem.TypeView
import app.familygem.R
import android.text.InputType
import android.view.View
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView
import android.view.View.OnFocusChangeListener
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filter.FilterResults
import java.util.*

/**
 * Create a combo box to choose a type text from a list of predefined values
 */
class TypeView(context: Context, combo: Combo?) : AppCompatAutoCompleteTextView(context) {
    enum class Combo {
        NAME, RELATIONSHIP
    }

    var completeTypes: MutableList<String> = ArrayList()

    init {
        val types = getTypes(combo)
        for (type in types!!.keys) {
            completeTypes.add(
                if (Locale.getDefault().language != "en") "$type - " + context.getString(
                    types[type]!!
                ) else type // Translation into all languages other than English
            )
        }
        val listAdapter: ListAdapter =
            ListAdapter(context, android.R.layout.simple_spinner_dropdown_item, completeTypes)
        setAdapter(listAdapter)
        id = R.id.fatto_edita
        //setThreshold(0); // useless, the minimum is 1
        inputType = InputType.TYPE_CLASS_TEXT
        onItemClickListener =
            OnItemClickListener { parent: AdapterView<*>?, view: View?, position: Int, id: Long ->
                setText(
                    types.keys.toTypedArray()[position]
                )
            }
        onFocusChangeListener =
            OnFocusChangeListener { view: View?, hasFocus: Boolean -> if (hasFocus) showDropDown() }
    }

    override fun enoughToFilter(): Boolean {
        return true // Always show hints
    }

    internal inner class ListAdapter(context: Context?, piece: Int, strings: List<String?>?) :
        ArrayAdapter<String?>(
            context!!, piece, strings!!
        ) {
        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence): FilterResults {
                    val result = FilterResults()
                    result.values = completeTypes
                    result.count = completeTypes.size
                    return result
                }

                override fun publishResults(constraint: CharSequence, results: FilterResults) {
                    notifyDataSetChanged()
                }
            }
        }
    }

    companion object {
        fun getTypes(combo: Combo?): Map<String, Int>? {
            return when (combo) {
                Combo.NAME -> ImmutableMap(
                    "aka", R.string.aka,
                    "birth", R.string.birth,
                    "immigrant", R.string.immigrant,
                    "maiden", R.string.maiden,
                    "married", R.string.married
                )
                Combo.RELATIONSHIP -> ImmutableMap(
                    "unknown", R.string.unknown_relationship,
                    "marriage", R.string.marriage,
                    "not married", R.string.not_married,
                    "civil", R.string.civil_marriage,
                    "religious", R.string.religious_marriage,
                    "common law", R.string.common_law_marriage,
                    "partnership", R.string.partnership,
                    "registered partnership", R.string.registered_partnership,
                    "living together", R.string.living_together,
                    "living apart together", R.string.living_apart_together
                )
                else -> null
            }
        }

        /**
         * Create a Map from a list of values
         */
        fun ImmutableMap(vararg keyValPair: Any): Map<String, Int> {
            val map: MutableMap<String, Int> = LinkedHashMap()
            require(keyValPair.size % 2 == 0) { "Keys and values must be pairs." }
            var i = 0
            while (i < keyValPair.size) {
                map[keyValPair[i] as String] = keyValPair[i + 1] as Int
                i += 2
            }
            return Collections.unmodifiableMap(map)
        }

        /**
         * Find the translation for predefined English types, or returns the provided type
         */
        fun getTranslatedType(type: String, combo: Combo?): String {
            val types = getTypes(combo)
            val translation = types!![type]
            return if (translation != null) Global.context!!.getString(translation)
                .lowercase(Locale.getDefault()) else type
        }
    }
}