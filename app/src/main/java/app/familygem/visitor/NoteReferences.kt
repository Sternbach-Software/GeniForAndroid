package app.familygem.visitor

import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.NoteContainer
import java.util.LinkedHashSet

/**
 * Visitor per shared note with a triple function:
 * - To count in all the elements of the Gedcom the references to the shared note
 * - Delete references to the note
 * - In the meantime, collect all the founders
 *
 * Visitatore per nota condivisa con una triplice funzione:
 * - Contare in tutti gli elementi del Gedcom i riferimenti alla nota condivisa
 * - Eliminare i riferimenti alla nota
 * - Nel frattempo raccogliere tutti i capostipiti
 */
class NoteReferences(
    gc: Gedcom, // Id of the shared note
    private val id: String, // flag to eliminate the refs to the note rather than counting them //bandierina per eliminare i ref alla nota piuttosto che contarli
    private val deleteRefs: Boolean
) : TotalVisitor() {

    private var head: Any? = null

    var count = 0 // references to the shared note

    val founders: MutableSet<Any?> = LinkedHashSet()

    init {
        gc.accept(this)
    }

    public override fun visit(obj: Any, isProgenitor: Boolean): Boolean {
        if (isProgenitor) head = obj
        if (obj is NoteContainer) {
            val refs = obj.noteRefs.iterator()
            while (refs.hasNext()) {
                val nr = refs.next()
                if (nr.ref == id) {
                    founders.add(head)
                    if (deleteRefs) refs.remove() else count++
                }
            }
            if (obj.noteRefs.isEmpty()) obj.noteRefs = null
        }
        return true
    }
}