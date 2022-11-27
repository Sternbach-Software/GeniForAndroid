package app.familygem

import app.familygem.Memory.Companion.setInstanceAndAllSubsequentToNull
import app.familygem.SourcesFragment.LibraryAdapter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.familygem.R
import app.familygem.SourcesFragment
import app.familygem.SourcesFragment.SourceViewHolder
import android.widget.Filterable
import android.widget.Filter.FilterResults
import android.widget.TextView
import android.content.Intent
import android.app.Activity
import android.content.Context
import android.view.*
import app.familygem.Memory
import app.familygem.detail.SourceActivity
import app.familygem.U
import android.view.ContextMenu.ContextMenuInfo
import android.widget.Filter
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import app.familygem.constant.intdefs.CITATION_KEY
import app.familygem.constant.intdefs.LIBRARY_CHOOSE_SOURCE_KEY
import app.familygem.constant.intdefs.SOURCE_ID_KEY
import app.familygem.visitor.ListOfSourceCitations
import app.familygem.visitor.ListOfSourceCitations.Triplet
import org.folg.gedcom.model.*
import java.util.*

/**
 * List of Sources (Sources)
 * Unlike [FamiliesFragment] it uses an adapter for the RecyclerView
 */
class SourcesFragment : Fragment() {
    private lateinit var listOfSources: List<Source>
    private var adapter: LibraryAdapter? = null
    private var order = 0
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        bandolo: Bundle?
    ): View? {
        listOfSources = Global.gc!!.sources
        (activity as AppCompatActivity?)!!.supportActionBar!!.setTitle(
            listOfSources.size.toString() + " " +
                    getString(if (listOfSources.size == 1) R.string.source else R.string.sources).lowercase(
                        Locale.getDefault()
                    )
        )
        if (listOfSources.size > 1) setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.sources, container, false)
        val sources = view.findViewById<RecyclerView>(R.id.sources_recycler)
        adapter = LibraryAdapter()
        sources.adapter = adapter
        view.findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
            createNewSource(
                context, null
            )
        }
        return view
    }

    inner class LibraryAdapter : RecyclerView.Adapter<SourceViewHolder>(), Filterable {
        override fun onCreateViewHolder(parent: ViewGroup, type: Int): SourceViewHolder {
            val sourceView = LayoutInflater.from(parent.context)
                .inflate(R.layout.source_item, parent, false)
            registerForContextMenu(sourceView)
            return SourceViewHolder(sourceView)
        }

        override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
            val source = listOfSources!![position]
            holder.id.text = source.id
            holder.id.visibility =
                if (order == 1 || order == 2) View.VISIBLE else View.GONE
            holder.title.text = sourceTitle(source)
            var times = source.getExtension(CITATION_KEY)
            // Count citations with my method
            if (times == null) {
                times = countCitations(source)
                source.putExtension(CITATION_KEY, times)
            }
            holder.times.text = times.toString()
        }

        // Filter source titles based on search words
        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(charSequence: CharSequence): FilterResults {
                    val query = charSequence.toString()
                    listOfSources = if (query.isEmpty()) {
                        Global.gc!!.sources
                    } else {
                        val filteredList: MutableList<Source> = ArrayList()
                        for (source in Global.gc!!.sources) {
                            if (sourceTitle(source).lowercase(Locale.getDefault())
                                    .contains(query.lowercase(Locale.getDefault()))
                            ) {
                                filteredList.add(source)
                            }
                        }
                        filteredList
                    }
                    sortSources() // Sorting the query reorders those that appear
                    val filterResults = FilterResults()
                    filterResults.values = listOfSources
                    return filterResults
                }

                override fun publishResults(cs: CharSequence, fr: FilterResults) {
                    notifyDataSetChanged()
                }
            }
        }

        override fun getItemCount(): Int {
            return listOfSources!!.size
        }
    }

    inner class SourceViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener {
        var id: TextView
        var title: TextView
        var times: TextView

        init {
            id = view.findViewById(R.id.source_id)
            title = view.findViewById(R.id.source_title)
            times = view.findViewById(R.id.source_num)
            view.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            // Returns the id of a source to IndividualPersonActivity and DetailActivity
            if (requireActivity().intent.getBooleanExtra(LIBRARY_CHOOSE_SOURCE_KEY, false)) {
                val intent = Intent()
                intent.putExtra(SOURCE_ID_KEY, id.text.toString())
                requireActivity().setResult(Activity.RESULT_OK, intent)
                requireActivity().finish()
            } else {
                val source = Global.gc!!.getSource(id.text.toString())
                Memory.setFirst(source)
                startActivity(Intent(context, SourceActivity::class.java))
            }
        }
    }

    override fun onPause() {
        super.onPause()
        requireActivity().intent.removeExtra(LIBRARY_CHOOSE_SOURCE_KEY)
    }

    /**
     * Sort the sources according to one of the criteria.
     * The order then becomes permanent in the Json.
     */
    private fun sortSources() {
        if (order > 0) {
            if (order == 5 || order == 6) {
                for (source in listOfSources!!) {
                    if (source.getExtension(CITATION_KEY) == null) source.putExtension(
                        CITATION_KEY,
                        countCitations(source)
                    )
                }
            }
            Collections.sort(listOfSources) { f1: Source, f2: Source ->
                when (order) {
                    1 -> return@sort U.extractNum(f1.id) - U.extractNum(f2.id)
                    2 -> return@sort U.extractNum(f2.id) - U.extractNum(f1.id)
                    3 -> return@sort sourceTitle(f1).compareTo(sourceTitle(f2), ignoreCase = true)
                    4 -> return@sort sourceTitle(f2).compareTo(sourceTitle(f1), ignoreCase = true)
                    5 -> return@sort U.castJsonInt(f1.getExtension(CITATION_KEY)) - U.castJsonInt(
                        f2.getExtension(
                            CITATION_KEY
                        )
                    )
                    6 -> return@sort U.castJsonInt(f2.getExtension(CITATION_KEY)) - U.castJsonInt(
                        f1.getExtension(
                            CITATION_KEY
                        )
                    )
                }
                0
            }
        }
    }

    private var count = 0

    /**
     * Returns how many times a source is cited in Gedcom
     * I tried to rewrite it as Visitor, but it's much slower
     */
    private fun countCitations(source: Source): Int {
        count = 0
        for (p in Global.gc!!.people) {
            countCitations(p, source)
            for (n in p.names) countCitations(n, source)
            for (ef in p.eventsFacts) countCitations(ef, source)
        }
        for (f in Global.gc!!.families) {
            countCitations(f, source)
            for (ef in f.eventsFacts) countCitations(ef, source)
        }
        for (n in Global.gc!!.notes) countCitations(n, source)
        return count
    }

    /**
     * receives an Object (Person, Name, EventFact...) and counts how many times the source is cited
     */
    private fun countCitations(`object`: Any, source: Source) {
        val sourceCitations: List<SourceCitation>
        sourceCitations = if (`object` is Note) // if it is a Note
            `object`.sourceCitations else {
            for (n in (`object` as NoteContainer).notes) countCitations(n, source)
            (`object` as SourceCitationContainer).sourceCitations
        }
        for (sc in sourceCitations) {
            if (sc.ref != null) if (sc.ref == source.id) count++
        }
    }

    // options menu in the toolbar
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val subMenu = menu.addSubMenu(R.string.order_by)
        if (Global.settings!!.expert) subMenu.add(0, 1, 0, R.string.id)
        subMenu.add(0, 2, 0, R.string.title)
        subMenu.add(0, 3, 0, R.string.citations)

        // Search in the Library
        inflater.inflate(R.menu.search, menu)
        val searchView = menu.findItem(R.id.search_item).actionView as SearchView?
        searchView!!.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                adapter!!.filter.filter(query)
                return true
            }

            override fun onQueryTextSubmit(q: String): Boolean {
                searchView.clearFocus()
                return false
            }
        })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id > 0 && id <= 3) {
            if (order == id * 2 - 1) order++ else if (order == id * 2) order-- else order =
                id * 2 - 1
            sortSources()
            adapter!!.notifyDataSetChanged()
            return true
        }
        return false
    }

    private var source: Source? = null
    override fun onCreateContextMenu(menu: ContextMenu, vista: View, info: ContextMenuInfo?) {
        source =
            Global.gc!!.getSource((vista.findViewById<View>(R.id.source_id) as TextView).text.toString())
        if (Global.settings!!.expert) menu.add(0, 0, 0, R.string.edit_id)
        menu.add(0, 1, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 0) { // Edit source ID
            source?.let { U.editId(requireContext(), it) { requireActivity().recreate() } }
        } else if (item.itemId == 1) { // Delete source
            val objects = deleteSource(source)
            U.save(false, *objects)
            requireActivity().recreate()
        } else {
            return false
        }
        return true
    }

    companion object {
        /**
         * Returns the title of the source
         */
        @JvmStatic
        fun sourceTitle(fon: Source?): String {
            var tit = ""
            if (fon != null) if (fon.abbreviation != null) tit =
                fon.abbreviation else if (fon.title != null) tit =
                fon.title else if (fon.text != null) {
                tit = fon.text.replace("\n".toRegex(), " ")
                //tit = tit.length() > 35 ? tit.substring(0,35)+"…" : tit;
            } else if (fon.publicationFacts != null) {
                tit = fon.publicationFacts.replace("\n".toRegex(), " ")
            }
            return tit
        }

        fun createNewSource(context: Context?, container: Any?) {
            val source = Source()
            source.id = U.newID(Global.gc!!, Source::class.java)
            source.title = ""
            Global.gc!!.addSource(source)
            if (container != null) {
                val sourceCitation = SourceCitation()
                sourceCitation.ref = source.id
                if (container is Note) container.addSourceCitation(sourceCitation) else (container as SourceCitationContainer).addSourceCitation(
                    sourceCitation
                )
            }
            U.save(true, source)
            Memory.setFirst(source)
            context!!.startActivity(Intent(context, SourceActivity::class.java))
        }

        /**
         * // Remove the source, the Refs in all SourceCitations pointing to it, and empty SourceCitations
         * All citations to the deleted Source become [[Source]]s to which a Source should be able to be reattached
         * @return an array of modified parents
         */
        fun deleteSource(source: Source?): Array<Any> {
            val citations = ListOfSourceCitations(Global.gc!!, source!!.id)
            for (citation in citations.list) {
                val sc = citation.citation
                sc!!.ref = null
                // If the SourceCitation contains nothing else, it can be deleted
                var deletable = true
                if (sc.page != null || sc.date != null || sc.text != null || sc.quality != null || !sc.getAllNotes(
                        Global.gc
                    ).isEmpty() || !sc.getAllMedia(Global.gc).isEmpty() || !sc.extensions.isEmpty()
                ) deletable = false
                if (deletable) {
                    val container = citation.container
                    var list: MutableList<SourceCitation?>
                    list =
                        if (container is Note) container.sourceCitations else (container as SourceCitationContainer?)!!.sourceCitations
                    list.remove(sc)
                    if (list.isEmpty()) {
                        if (container is Note) container.sourceCitations =
                            null else (container as SourceCitationContainer?)!!.sourceCitations =
                            null
                    }
                }
            }
            Global.gc!!.sources.remove(source)
            if (Global.gc!!.sources.isEmpty()) Global.gc!!.sources = null
            Global.gc!!.createIndexes() // necessary
            setInstanceAndAllSubsequentToNull(source)
            return citations.progenitors
        }
    }
}