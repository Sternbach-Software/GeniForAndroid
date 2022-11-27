package app.familygem.visitor

import app.familygem.Memory
import org.folg.gedcom.model.*

/**
 *
 * // Visitor that produces in Memory the hierarchical stack of objects between the parent record and a given object
 * // e.g. Person> Simple media
 * // or Family> Note> SourceCitation> Simple Note
 *
 * // Visitatore che produce in Memoria la pila gerarchica degli oggetti tra il record capostipite e un object dato
 * // ad es. Person > Media semplice
 * // oppure Family > Note > SourceCitation > Note semplice
 */
class FindStack(gc: Gedcom, scopo: Any) : Visitor() {

    private val stack: MutableList<Memory.Step>
    private val scope: Any
    private var found = false

    init {
        stack = Memory.addStack() //in a new stack on purpose
        scope = scopo
        gc.accept(this)
    }

    private fun opera(obj: Any, tag: String, progenitor: Boolean): Boolean {
        if (!found) {
            if (progenitor) stack.clear() // every progenitor makes a stack start all over again
            val step = Memory.Step()
            step.obj = obj
            step.tag = tag
            if (!progenitor) step.clearStackOnBackPressed =
                true // onBackPressed marks them to delete them in bulk
            stack.add(step)
        }
        if (obj == scope) {
            val steps = stack.iterator()
            while (steps.hasNext()) {
                val janitor = CleanStack(scope)
                (steps.next().obj as Visitable).accept(janitor)
                if (janitor.toDelete) steps.remove()
            }
            found = true
            //Memoria.stampa("FindStack"); log?
        }
        return true
    }

    override fun visit(step: Header): Boolean {
        return opera(step, "HEAD", true)
    }

    override fun visit(step: Person): Boolean {
        return opera(step, "INDI", true)
    }

    override fun visit(step: Family): Boolean {
        return opera(step, "FAM", true)
    }

    override fun visit(step: Source): Boolean {
        return opera(step, "SOUR", true)
    }

    override fun visit(step: Repository): Boolean {
        return opera(step, "REPO", true)
    }

    override fun visit(step: Submitter): Boolean {
        return opera(step, "SUBM", true)
    }

    override fun visit(step: Media): Boolean {
        return opera(step, "OBJE", step.id != null)
    }

    override fun visit(step: Note): Boolean {
        return opera(step, "NOTE", step.id != null)
    }

    override fun visit(step: Name): Boolean {
        return opera(step, "NAME", false)
    }

    override fun visit(step: EventFact): Boolean {
        return opera(step, step.tag, false)
    }

    override fun visit(step: SourceCitation): Boolean {
        return opera(step, "SOUR", false)
    }

    override fun visit(step: RepositoryRef): Boolean {
        return opera(step, "REPO", false)
    }

    override fun visit(step: Change): Boolean {
        return opera(step, "CHAN", false)
    } /* ok but then GedcomTag is not Visitable and therefore does not continue the visit
	@Override
	public boolean visit( String chiave, Object estensioni ) {
		if( chiave.equals("folg.more_tags") ) {
			for( GedcomTag est : (List<GedcomTag>)estensioni ) {
				//s.l(est.getClass().getName()+" "+est.getTag());
				opera( est, est.getTag(), false );
			}
		}
		return true;
	}*/
}