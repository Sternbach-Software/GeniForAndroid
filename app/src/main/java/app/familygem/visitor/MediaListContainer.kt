package app.familygem.visitor

import org.folg.gedcom.model.*

/**
 * Ordered map of the media each with its own container object
 * The container is used practically only to disconnectMedia in IndividualMedia
 *
 * Mappa ordinata dei media ciascuno col suo object contenitore
 * Il contenitore serve praticamente solo a scollegaMedia in IndividuoMedia
 */
class MediaListContainer(
    private val gc: Gedcom, // List all media (even local) or shared media objects only //Elencare tutti i media (anche i locali) o solo gli oggetti media condivisi
    private val requestAll: Boolean
) : Visitor() {
    val mediaList = mutableSetOf<MediaWithContainer>()
    private fun visitInternal(any: Any): Boolean {
        if (requestAll && any is MediaContainer) {
            //for( MediaRef r : p.getMediaRefs() ) listaMedia.put( r.getMedia(gc), p );	//list empty refs => null media // elenca i ref a vuoto => media null
            for (med in any.getAllMedia(gc)) { // Media objects and local media of each record //Oggetti media e media locali di ciascun record
                val mediaWithContainer = MediaWithContainer(med, any)
                mediaList.add(mediaWithContainer)
            }
        }
        return true
    }

    override fun visit(gc: Gedcom): Boolean {
        for (med in gc.media) mediaList.add(
            MediaWithContainer(
                med,
                gc
            )
        ) // (rake?) the media items //rastrella gli oggetti media
        return true
    }

    override fun visit(p: Person): Boolean {
        return visitInternal(p)
    }

    override fun visit(f: Family): Boolean {
        return visitInternal(f)
    }

    override fun visit(e: EventFact): Boolean {
        return visitInternal(e)
    }

    override fun visit(n: Name): Boolean {
        return visitInternal(n)
    }

    override fun visit(c: SourceCitation): Boolean {
        return visitInternal(c)
    }

    override fun visit(s: Source): Boolean {
        return visitInternal(s)
    }

    /**
     * Class representing a Media with its container object
     */
    data class MediaWithContainer(var media: Media) {
        lateinit var container: Any
        /**
         * Secondary constructor is to keep the same 2-arg constructor API, be able to use `data class`,
         * but to not include [container] in [equals] - instead of defining custom [equals] and [hashCode]
         * */
        constructor(_media: Media, _container: Any): this(_media) {
            container = _container
        }
    }
}