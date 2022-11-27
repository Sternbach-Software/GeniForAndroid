package app.familygem.list

import android.widget.Filterable
import android.view.LayoutInflater
import android.view.ViewGroup
import app.familygem.R
import app.familygem.visitor.NoteReferences
import android.widget.TextView
import android.view.View
import android.widget.Filter
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Global
import org.folg.gedcom.model.Note
import java.util.*

class NotesAdapter(
    initialList: List<Note>,
    private val sharedOnly: Boolean,
    val onClickListener: (Int) -> Unit
) : RecyclerView.Adapter<NotesAdapter.ViewHolder>(), Filterable {

    private val noteList = initialList.toMutableList()
    lateinit var selectedNote: Note

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.notes_item, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val note = getItem(position)
        if (note.id == null) holder.countView.visibility = View.GONE
        else {
            holder.countView.visibility = View.VISIBLE
            holder.countView.text = NoteReferences(Global.gc, note.id, false).count.toString()
        }
        holder.itemView.tag = note // for the Delete context menu
        holder.textView.text = note.value
    }

    override fun getItemCount(): Int = noteList.size
    fun getItem(index: Int): Note = noteList[index]

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(charSequence: CharSequence): FilterResults {
                val query = charSequence.toString()
                noteList.clear()
                noteList.addAll(NotesFragment.getAllNotes(sharedOnly))
                if (query.isNotEmpty()) {
                    val noteIterator = noteList.iterator()
                    while (noteIterator.hasNext()) {
                        val note = noteIterator.next()
                        if (note.value == null || !note.value.lowercase(Locale.getDefault())
                                .contains(
                                    query.lowercase(
                                        Locale.getDefault()
                                    )
                                )
                        ) {
                            noteIterator.remove()
                        }
                    }
                }
                val filterResults = FilterResults()
                filterResults.values = noteList
                return filterResults
            }

            override fun publishResults(cs: CharSequence, fr: FilterResults) {
                notifyDataSetChanged() //TODO refactor to ListAdapter
            }
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView: TextView = itemView.findViewById(R.id.note_text)
        val countView: TextView = itemView.findViewById(R.id.note_citations)

        init {
            itemView.setOnClickListener {
                onClickListener(bindingAdapterPosition)
            }
            itemView.setOnCreateContextMenuListener { menu, v, _ ->
                selectedNote = v.tag as Note
                menu.add(0, 0, 0, R.string.delete)
            }
        }
    }
}