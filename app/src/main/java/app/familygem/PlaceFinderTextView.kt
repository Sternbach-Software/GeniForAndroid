package app.familygem

import android.R
import android.content.Context
import androidx.appcompat.widget.AppCompatAutoCompleteTextView
import android.text.InputType
import android.util.AttributeSet
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.Filter.FilterResults
import org.geonames.*
import java.lang.Exception
import java.util.*

/**
 * TextView that suggests Gedcom-style place names using GeoNames
 */
class PlaceFinderTextView(context: Context?, `as`: AttributeSet?) : AppCompatAutoCompleteTextView(
    context!!, `as`
) {
    var searchCriteria: ToponymSearchCriteria

    init {
        val listAdapter: ListAdapter = ListAdapter(context, R.layout.simple_spinner_dropdown_item)
        setAdapter(listAdapter)
        inputType = InputType.TYPE_TEXT_FLAG_CAP_WORDS
        //setThreshold(2);

        // GeoNames settings
        WebService.setUserName(BuildConfig.utenteGeoNames)
        searchCriteria = ToponymSearchCriteria()
        searchCriteria.language = Locale.getDefault().language // en, es, it...
        searchCriteria.style = Style.FULL
        searchCriteria.maxRows = 3
        //searchCriteria.setFuzzy(0.9); // Not with setNameStartsWith
        //searchCriteria.setFeatureClass( FeatureClass.A ); // either one or the other
        //searchCriteria.setFeatureClass( FeatureClass.P );
    }

    internal inner class ListAdapter(context: Context?, piece: Int) : ArrayAdapter<String>(
        context!!, piece
    ), Filterable {
        var places: MutableList<String>

        init {
            places = ArrayList()
        }

        override fun getCount(): Int {
            return places.size
        }

        override fun getItem(index: Int): String {
            return if (places.size > 0 && index < places.size) places[index] else ""
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence): FilterResults {
                    val filterResults = FilterResults()
                    if (constraint != null) {
                        //searchCriteria.setQ(constraint.toString());
                        searchCriteria.nameStartsWith = constraint.toString()
                        try {
                            val searchResult = WebService.search(searchCriteria)
                            places.clear()
                            for (topo in searchResult.toponyms) {
                                var str = topo.name // Toponym
                                if (topo.adminName4 != null && topo.adminName4 != str) str += ", " + topo.adminName4 // Village
                                if (topo.adminName3 != null && !str!!.contains(topo.adminName3)) str += ", " + topo.adminName3 // municipality
                                if (!topo.adminName2.isEmpty() && !str!!.contains(topo.adminName2)) str += ", " + topo.adminName2 // Province/district
                                if (!str!!.contains(topo.adminName1)) str += ", " + topo.adminName1 // Region
                                if (!str.contains(topo.countryName)) str += ", " + topo.countryName // Country
                                if (str != null && !places.contains(str)) // Avoid null and duplicates
                                    places.add(str)
                            }
                            filterResults.values = places
                            filterResults.count = places.size
                        } catch (e: Exception) {
                        }
                    }
                    return filterResults
                }

                override fun publishResults(constraint: CharSequence, results: FilterResults) {
                    if (results != null && results.count > 0) {
                        notifyDataSetChanged()
                    } else {
                        notifyDataSetInvalidated()
                    }
                }
            }
        }
    }
}