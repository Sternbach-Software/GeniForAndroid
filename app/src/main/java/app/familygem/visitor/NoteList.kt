package app.familygem.visitor

import app.familygem.Global
import app.familygem.visitor.TotalVisitor
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.Source
import java.util.ArrayList

/**
 * Visitor producing an ordered map of INLINE notes
 */
class NoteList : TotalVisitor() {

    val noteList = mutableListOf<Note>()

    public override fun visit(obj: Any, isProgenitor: Boolean): Boolean {
        if (obj is NoteContainer
            && !(!Global.settings.expert && (obj is Source || obj is Repository))
        ) {
            noteList.addAll(obj.notes)
        }
        return true
    }
}