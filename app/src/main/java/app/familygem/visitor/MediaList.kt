package app.familygem.visitor

import app.familygem.F.mediaPath
import app.familygem.Global
import app.familygem.constant.intdefs.*
import org.folg.gedcom.model.*
import java.util.LinkedHashSet

/**
 * Ordered set of media
 * Can almost always replace ContainerMediaList
 */
class MediaList(
    private val gc: Gedcom,
    @MediaType
    private val mediaType: Int
) : Visitor() {

    /**
     * This uses [LinkedHashSet] to maintain this class's iteration order guarantee.
     * Although [mutableSetOf] guarantees iteration order by using [LinkedHashSet], this is more explicit.
     * */
	val list: MutableSet<Media> = LinkedHashSet()

    private fun visita(any: Any): Boolean {
        if (any is MediaContainer) {
            when (mediaType) {
                ALL_MEDIA -> list.addAll(any.getAllMedia(gc)) // adds shared and local media
                LOCAL_MEDIA-> list.addAll(any.media) // local media only
                SHARED_AND_LOCAL_MEDIA -> for (med in any.getAllMedia(
                    gc
                )) filter(med)
                SHARED_MEDIA -> {} //TODO why didn't he handle this?
            }
        }
        return true
    }

    /**
     * Adds only the alleged (pretty? - "bellini") ones with preview
     * Aggiunge solo quelli presunti bellini con anteprima
     */
    private fun filter(media: Media) {
        val file = mediaPath(Global.settings.openTree, media) // TODO and images from URIs?
        if (file != null) {
            val index = file.lastIndexOf('.')
            if (index > 0) {
                when (file.substring(index + 1)) {
                    "jpg",
                    "jpeg",
                    "png",
                    "gif",
                    "bmp",
                    "webp",
                    "heic",
                    "heif",
                    "mp4",
                    "3gp",
                    "webm",
                    "mkv" -> list.add(
                        media
                    )
                }
            }
        }
    }

    override fun visit(gc: Gedcom): Boolean {
        if (mediaType == ALL_MEDIA || mediaType == SHARED_MEDIA) list.addAll(gc.media) // (rakes?) all Gedcom shared media items //rastrella tutti gli oggetti media condivisi del Gedcom
        else if (mediaType == SHARED_AND_LOCAL_MEDIA) for (med in gc.media) filter(med) //TODO what about LOCAL_MEDIA?
        return true
    }

    override fun visit(p: Person): Boolean {
        return visita(p)
    }

    override fun visit(f: Family): Boolean {
        return visita(f)
    }

    override fun visit(e: EventFact): Boolean {
        return visita(e)
    }

    override fun visit(n: Name): Boolean {
        return visita(n)
    }

    override fun visit(c: SourceCitation): Boolean {
        return visita(c)
    }

    override fun visit(s: Source): Boolean {
        return visita(s)
    }
}