package app.familygem.visitor

import org.folg.gedcom.model.*

/**
 * Visitor model that visits all the possible Gedcom containers distinguishing the progenitors
 * Modello di Visitor che visita tutti i possibili contenitori del Gedcom distinguendo i capostipiti
 */
open class TotalVisitor : Visitor() {
    private fun visitInternal(`object`: Any) = visit(`object`, false)

    open fun visit(obj: Any?, isProgenitor: Boolean) = true
    override fun visit(h: Header) = visit(h, true)
    override fun visit(p: Person) = visit(p, true)
    override fun visit(f: Family) = visit(f, true)
    override fun visit(s: Source) = visit(s, true)
    override fun visit(r: Repository) = visit(r, true)
    override fun visit(s: Submitter) = visit(s, true)
    override fun visit(m: Media) = visit(m, m.id != null)
    override fun visit(n: Note) = visit(n, n.id != null)
    override fun visit(n: Name) = visitInternal(n)
    override fun visit(e: EventFact) = visitInternal(e)
    override fun visit(s: SourceCitation) = visitInternal(s)
    override fun visit(r: RepositoryRef) = visitInternal(r)
    override fun visit(c: Change) = visitInternal(c)
}