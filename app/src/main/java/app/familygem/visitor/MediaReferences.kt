package app.familygem.visitor

import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import java.util.LinkedHashSet

/**
 *
 * Visitor who, compared to a shared media, has a triple function:
 * * - Count media references in all MediaContainers
 * * - Or delete the same media references
 * * - In the meantime, list the parent objects of the stacks that contain the media
 *
 *
 * Visitatore che rispetto a un media condiviso ha una triplice funzione:
 * - Contare i riferimenti al media in tutti i MediaContainer
 * - Oppure elimina gli stessi riferimenti al media
 * - Nel frattempo elenca gli oggetti capostipite delle pile che contengono il media
 */
class MediaReferences(
    gc: Gedcom, // the shared media
    private val media: Media, private val shouldEliminateRef: Boolean
) : TotalVisitor() {
    private var progenitor // the progenitor of the stack
            : Any? = null
    var num = 0 // the number of references to a Media
    var founders: MutableSet<Any?> =
        LinkedHashSet() // the list of the founding objects containing a Media//l'elenco degli oggetti capostipiti contenti un Media

    init {
        gc.accept(this)
    }

    public override fun visit(obj: Any, isProgenitor: Boolean): Boolean {
        if (isProgenitor) progenitor = obj
        if (obj is MediaContainer) {
            val mediaRefs = obj.mediaRefs.iterator()
            while (mediaRefs.hasNext()) if (mediaRefs.next().ref == media.id) {
                founders.add(progenitor)
                if (shouldEliminateRef) mediaRefs.remove() else num++
            }
            if (obj.mediaRefs.isEmpty()) obj.mediaRefs = null
        }
        return true
    }
}