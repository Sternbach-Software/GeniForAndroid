package app.familygem.visitor

import org.folg.gedcom.model.Gedcom
import app.familygem.visitor.TotalVisitor
import app.familygem.visitor.ListOfSourceCitations.Triplet
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.SourceCitationContainer
import org.folg.gedcom.model.SourceCitation
import java.util.ArrayList
import java.util.LinkedHashSet

/**
 * // Starting from the id of a source it generates a list of triplets: parent / container / citations of the source
 * // Used by [LibraryFragment], [SourceActivity] and [ConfirmationActivity]
 *
 * // Partendo dall'id di una fonte genera una lista di triplette: capostipite / contenitore / citazioni della fonte
 * // Usato da Biblioteca, da Fonte e da Conferma
 */
class ListOfSourceCitations(
    gc: Gedcom, // id of the source
    private val id: String
) : TotalVisitor() {
    val list = mutableListOf<Triplet>()
    private var capo: Any? = null

    init {
        gc.accept(this)
    }

    public override fun visit(obj: Any, isProgenitor: Boolean): Boolean {
        if (isProgenitor) capo = obj
        if (obj is SourceCitationContainer) {
            analyze(obj, obj.sourceCitations)
        } // Note does not extend SourceCitationContainer, but implements its own methods
        else if (obj is Note) {
            analyze(obj, obj.sourceCitations)
        }
        return true
    }

    private fun analyze(container: Any, citations: List<SourceCitation>) {
        for (citation in citations)  // (Known sources?)[SourceCitations?] have no Ref to a source //Le fonti-note non hanno Ref ad una fonte
            if (citation.ref != null && citation.ref == id) {
                val triplet = Triplet()
                triplet.progenitor = capo
                triplet.container = container
                triplet.citation = citation
                list.add(triplet)
            }
    }

    // merge duplicates
    val progenitors: Array<Any>
        get() {
            val heads: MutableSet<Any> = LinkedHashSet() // merge duplicates TODO why linked?
            for (tri in list) {
                tri.progenitor?.let { heads.add(it) }
            }
            return heads.toTypedArray()
        }

    /**
     * Class for storing together the three parent elements - container - quote
     * Classe per stoccare insieme i tre elementi capostipite - contenitore - citazione
     */
    class Triplet {
        var progenitor: Any? = null
        var container // It would be a SourceCitationContainer but Note is an exception //Sarebbe un SourceCitationContainer ma Note fa eccezione
                : Any? = null
        var citation: SourceCitation? = null
    }
}