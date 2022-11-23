package app.familygem.visitor

import org.folg.gedcom.model.*

/**
 * Counter of citations of a source
 * It would be used to replace [U.countCitations] (Source) in the Library
 * is more accurate in counting, that is, nothing escapes him, but is four times slower.
 *
 * // Contatore delle citazioni di una fonte
 * // Servirebbe per sostituire U.quanteCitazioni(Source) in Biblioteca
 * // è più preciso nella conta cioè non gli sfugge nulla, ma è quattro volte più lento
 */
class CountSourceCitation(var id: String) : Visitor() {
    var count = 0
    override fun visit(p: Person): Boolean {
        for (c in p.sourceCitations) if (c.ref != null) // required because source-notes have no Ref at source
            if (c.ref == id) count++
        return true
    }

    override fun visit(f: Family): Boolean {
        for (c in f.sourceCitations) if (c.ref != null) if (c.ref == id) count++
        return true
    }

    override fun visit(n: Name): Boolean {
        for (c in n.sourceCitations) if (c.ref != null) if (c.ref == id) count++
        return true
    }

    override fun visit(e: EventFact): Boolean {
        for (c in e.sourceCitations) if (c.ref != null) if (c.ref == id) count++
        return true
    }

    override fun visit(n: Note): Boolean {
        for (c in n.sourceCitations) if (c.ref != null) if (c.ref == id) count++
        return true
    }
}