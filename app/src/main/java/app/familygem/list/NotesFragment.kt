package app.familygem.list

import android.content.Context
import android.os.Bundle
import app.familygem.R
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.view.*
import androidx.appcompat.widget.SearchView
import app.familygem.detail.NoteActivity
import app.familygem.Memory
import app.familygem.visitor.FindStack
import app.familygem.U
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.familygem.Global
import app.familygem.constant.Choice
import app.familygem.visitor.NoteList
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteRef
import org.folg.gedcom.model.NoteContainer
import java.util.*

class NotesFragment : Fragment() {

    lateinit var adapter: NotesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        bandolo: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.recycler_view, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        val sharedOnly = activity?.intent?.getBooleanExtra(Choice.NOTE, false) ?: return null //TODO is this good form? We can't generate the view if the activity is null, and I don't want to throw an exception with requireActivity(). Is it a safe assumption that if activity is not null, then intent will also be not null?
        val allNotes = getAllNotes(sharedOnly)
        adapter = NotesAdapter(allNotes, sharedOnly) {
            val note = adapter.getItem(it)
            // Returns the id of a note to IndividualPersonActivity and DetailActivity
            if (requireActivity().intent?.getBooleanExtra(Choice.NOTE, false) == true) {
                val intent = Intent()
                intent.putExtra("noteId", note.id)
                activity?.setResult(AppCompatActivity.RESULT_OK, intent)
                activity?.finish()
            } else { // Opens the detail of the note
                val intent = Intent(context, NoteActivity::class.java)
                if (note.id != null) { // Shared note
                    Memory.setFirst(note)
                } else { // Simple note
                    FindStack(Global.gc, note)
                    intent.putExtra("fromNotes", true)
                }
                context?.startActivity(intent)
            }
        }
        recyclerView.adapter = adapter
        (activity as AppCompatActivity).supportActionBar!!.title = (allNotes.size.toString() + " "
                + getString(if (allNotes.size == 1) R.string.note else R.string.notes).lowercase(
            Locale.getDefault()
        ))
        setHasOptionsMenu(allNotes.size > 1)
        registerForContextMenu(recyclerView)
        view.findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
            newNote(
                context, null
            )
        }
        return view
    }

    /**
     * Leaving the activity without choosing a shared note resets the extra
     */
    override fun onPause() {
        super.onPause()
        activity?.intent?.removeExtra(Choice.NOTE)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        //TODO magic number
        if (item.itemId == 0) { // Delete
            val heads = U.deleteNote(adapter.selectedNote, null)
            U.save(false, *heads)
            activity?.recreate()
        } else {
            return false
        }
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        // Search inside notes
        inflater.inflate(R.menu.search, menu)
        val searchView = menu.findItem(R.id.search_item).actionView as SearchView?
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextChange(query: String): Boolean {
                adapter.filter.filter(query)
                return true
            }

            override fun onQueryTextSubmit(q: String): Boolean {
                searchView.clearFocus()
                return false
            }
        })
    }

    companion object {
        fun getAllNotes(sharedOnly: Boolean): List<Note> {
            // Shared notes
            val sharedNotes = Global.gc.notes
            val noteList = sharedNotes.toMutableList()
            // Inline notes
            if (!sharedOnly) {
                val noteVisitor = NoteList()
                Global.gc.accept(noteVisitor)
                noteList.addAll(noteVisitor.noteList)
            }
            return noteList
        }

        /**
         * Create a new shared note, attached to a container or unlinked
         */
		@JvmStatic
		fun newNote(context: Context?, container: Any?) {
            val note = Note()
            val id = U.newID(Global.gc, Note::class.java)
            note.id = id
            note.value = ""
            Global.gc.addNote(note)
            if (container != null) {
                val noteRef = NoteRef()
                noteRef.ref = id
                (container as NoteContainer).addNoteRef(noteRef)
            }
            U.save(true, note)
            Memory.setFirst(note)
            context?.startActivity(Intent(context, NoteActivity::class.java))
        }
    }
}