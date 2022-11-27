package app.familygem.visitor

import org.folg.gedcom.model.Gedcom
import app.familygem.visitor.TotalVisitor
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.NoteContainer
import org.folg.gedcom.model.NoteRef
import java.util.HashSet

/**
 * Visitor somewhat complementary to ReferencesNote, having a double function:
 * - Edit refs pointing to the shared note
 * - Collect a list of containers that include the shared Note
 *
 * Visitatore un po' complementare a RiferimentiNota, avente una doppia funzione:
 * - Modifica i ref che puntano alla nota condivisa
 * - Colleziona una lista dei contenitori che includono la Nota condivisa
 */
class NoteContainers(
    gc: Gedcom, // the shared note to search for
    private val note: Note, // the new id to put in the ref
    private val newId: String
) : TotalVisitor() {

    val containers = hashSetOf<NoteContainer>()

    init {
        gc.accept(this)
    }

    public override fun visit(obj: Any, isProgenitor: Boolean): Boolean {
        if (obj is NoteContainer) {
            for (noteRef in obj.noteRefs) {
                if (noteRef.ref == note.id) {
                    noteRef.ref = newId
                    containers.add(obj)
                }
            }
        }
        return true
    }
}