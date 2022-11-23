package app.familygem.visitor

import org.folg.gedcom.model.Gedcom
import app.familygem.visitor.TotalVisitor
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.MediaRef
import java.util.HashSet

/**
 * Visitor is somewhat complementary to ReferencesMedia, having a double function:
 * - Edit the refs pointing to the shared Media
 * - Collect a list of containers that include the shared media
 * Visitatore un po' complementare a RiferimentiMedia, avente una doppia funzione:
 * - Modifica i ref che puntano al Media condiviso
 * - Colleziona una lista dei contenitori che includono il Media condiviso
 */
class MediaContainers(gedcom: Gedcom, private val media: Media, private val newId: String) :
    TotalVisitor() {
    var containers = hashSetOf<MediaContainer>()

    init {
        gedcom.accept(this)
    }

    public override fun visit(obj: Any, isProgenitor: Boolean): Boolean {
        if (obj is MediaContainer) {
            for (mediaRef in obj.mediaRefs) {
                if (mediaRef.ref == media.id) {
                    mediaRef.ref = newId
                    containers.add(obj)
                }
            }
        }
        return true
    }
}