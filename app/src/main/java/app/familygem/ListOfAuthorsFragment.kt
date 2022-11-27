package app.familygem

import android.content.Context
import android.os.Bundle
import org.folg.gedcom.model.Submitter
import androidx.appcompat.app.AppCompatActivity
import app.familygem.R
import android.widget.LinearLayout
import android.widget.TextView
import app.familygem.TreeInfoActivity
import app.familygem.Memory
import android.content.Intent
import android.view.*
import app.familygem.detail.AuthorActivity
import app.familygem.ListOfAuthorsFragment
import app.familygem.U
import android.view.ContextMenu.ContextMenuInfo
import androidx.fragment.app.Fragment
import app.familygem.NewTreeActivity
import java.util.*

/**
 * List of Submitters (authors)
 */
class ListOfAuthorsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        stato: Bundle?
    ): View? {
        val authors = Global.gc!!.submitters
        (activity as AppCompatActivity?)!!.supportActionBar!!.title =
            authors.size.toString() + " " +
                    getString(if (authors.size == 1) R.string.submitter else R.string.submitters).lowercase(
                        Locale.getDefault()
                    )
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.scrollview, container, false)
        val layout = view.findViewById<LinearLayout>(R.id.scrollview_layout)
        for (author in authors) {
            val pieceView = inflater.inflate(R.layout.scrollview_item, layout, false)
            layout.addView(pieceView)
            (pieceView.findViewById<View>(R.id.item_name) as TextView).text =
                TreeInfoActivity.submitterName(author)
            pieceView.findViewById<View>(R.id.item_num).visibility = View.GONE
            pieceView.setOnClickListener { v: View? ->
                Memory.setFirst(author)
                startActivity(Intent(context, AuthorActivity::class.java))
            }
            registerForContextMenu(pieceView)
            pieceView.tag = author
        }
        view.findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
            newAuthor(context)
            U.save(true)
        }
        return view
    }

    // context Menu
    var subm: Submitter? = null
    override fun onCreateContextMenu(menu: ContextMenu, vista: View, info: ContextMenuInfo?) {
        subm = vista.tag as Submitter
        if (Global.gc!!.header == null || Global.gc!!.header.getSubmitter(Global.gc) == null || Global.gc!!.header.getSubmitter(
                Global.gc
            ) != subm
        ) menu.add(0, 0, 0, R.string.make_default)
        if (!U.submitterHasShared(subm!!)) // it can only be deleted if it has never been shared
            menu.add(0, 1, 0, R.string.delete)
        // todo explain why it can't be deleted?
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            0 -> {
                setMainSubmitter(subm)
                return true
            }
            1 -> {
                // Todo confirm deletion
                deleteAuthor(subm)
                U.save(false)
                requireActivity().recreate()
                return true
            }
        }
        return false
    }

    companion object {
        /**
         * Remove an author
         * All I know is that any SubmitterRef should be searched for in all records
         */
        fun deleteAuthor(aut: Submitter?) {
            val testa = Global.gc!!.header
            if (testa != null && testa.submitterRef != null && testa.submitterRef == aut!!.id) {
                testa.submitterRef = null
            }
            Global.gc!!.submitters.remove(aut)
            if (Global.gc!!.submitters.isEmpty()) Global.gc!!.submitters = null
            aut?.let { Memory.setInstanceAndAllSubsequentToNull(it) }
        }

        /**
         * Create a new Author, if it receives a context it opens it in editor mode
         */
        @JvmStatic
        fun newAuthor(context: Context?): Submitter {
            val submitter = Submitter()
            submitter.id = U.newID(Global.gc!!, Submitter::class.java)
            submitter.name = ""
            U.updateChangeDate(submitter)
            Global.gc!!.addSubmitter(submitter)
            if (context != null) {
                Memory.setFirst(submitter)
                context.startActivity(Intent(context, AuthorActivity::class.java))
            }
            return submitter
        }

        fun setMainSubmitter(subm: Submitter?) {
            var testa = Global.gc!!.header
            if (testa == null) {
                testa =
                    NewTreeActivity.createHeader(Global.settings!!.openTree.toString() + ".json")
                Global.gc!!.header = testa
            }
            testa!!.submitterRef = subm!!.id
            U.save(false, subm)
        }
    }
}