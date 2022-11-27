package app.familygem.detail

import app.familygem.visitor.NoteReferences
import android.app.Activity
import app.familygem.*
import app.familygem.constant.intdefs.FROM_NOTES_KEY
import org.folg.gedcom.model.Note

class NoteActivity : DetailActivity() {

    lateinit var n: Note

    override fun format() {
        n = cast(Note::class.java) as Note
        if (n.id == null) {
            setTitle(R.string.note)
            placeSlug("NOTE")
        } else {
            setTitle(R.string.shared_note)
            placeSlug("NOTE", n.id)
        }
        place(getString(R.string.text), "Value", true, true)
        place(getString(R.string.rin), "Rin", false, false)
        placeExtensions(n)
        U.placeSourceCitations(box, n)
        U.placeChangeDate(box, n.change)
        if (n.id != null) {
            val noteRef = NoteReferences(Global.gc!!, n.id, false)
            if (noteRef.count > 0) U.putContainer(
                box,
                noteRef.founders.toTypedArray(),
                R.string.shared_by
            )
        } else if ((box.context as Activity).intent.getBooleanExtra(FROM_NOTES_KEY, false)) {
            U.putContainer(box, Memory.firstObject(), R.string.written_in)
        }
    }

    override fun delete() {
        U.updateChangeDate(*U.deleteNote(n, null))
    }
}