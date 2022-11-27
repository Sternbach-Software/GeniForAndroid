package app.familygem

import app.familygem.Memory.StepStack
import app.familygem.Memory
import app.familygem.ProfileActivity
import app.familygem.detail.RepositoryActivity
import app.familygem.detail.RepositoryRefActivity
import app.familygem.detail.AuthorActivity
import app.familygem.detail.ChangesActivity
import app.familygem.detail.SourceCitationActivity
import app.familygem.detail.ExtensionActivity
import app.familygem.detail.EventActivity
import app.familygem.detail.FamilyActivity
import app.familygem.detail.SourceActivity
import app.familygem.detail.ImageActivity
import app.familygem.detail.AddressActivity
import app.familygem.detail.NameActivity
import app.familygem.detail.NoteActivity
import app.familygem.s
import org.folg.gedcom.model.*
import java.util.*

/**
 * Manages stacks of hierarchical objects for writing a breadcrumb trail in [DetailActivity]
 */
class Memory internal constructor() {
    var list: MutableList<StepStack> = ArrayList()

    init {
        classes[Person::class.java] = ProfileActivity::class.java
        classes[Repository::class.java] = RepositoryActivity::class.java
        classes[RepositoryRef::class.java] = RepositoryRefActivity::class.java
        classes[Submitter::class.java] = AuthorActivity::class.java
        classes[Change::class.java] = ChangesActivity::class.java
        classes[SourceCitation::class.java] = SourceCitationActivity::class.java
        classes[GedcomTag::class.java] = ExtensionActivity::class.java
        classes[EventFact::class.java] = EventActivity::class.java
        classes[Family::class.java] = FamilyActivity::class.java
        classes[Source::class.java] = SourceActivity::class.java
        classes[Media::class.java] = ImageActivity::class.java
        classes[Address::class.java] =
            AddressActivity::class.java
        classes[Name::class.java] = NameActivity::class.java
        classes[Note::class.java] = NoteActivity::class.java
    }

    class StepStack : Stack<Step?>()
    class Step {
        var obj: Any? = null
        var tag: String? = null
        var clearStackOnBackPressed // FindStack sets it to true then onBackPressed the stack must be deleted in bulk
                = false
    }

    companion object {
        var classes: MutableMap<Class<*>, Class<*>> = HashMap()
        private val memory = Memory()// an empty stack that is not added to the list

        /**
         * Return the last created stack if there is at least one
         * or return an empty one just to not return null
         */
        val stepStack: StepStack
            get() = if (memory.list.size > 0) memory.list[memory.list.size - 1] else StepStack() // an empty stack that is not added to the list

        fun addStack(): StepStack {
            val stepStack = StepStack()
            memory.list.add(stepStack)
            return stepStack
        }

        /**
         * Adds the first object to a new stack
         */
        fun setFirst(`object`: Any?) {
            setFirst(`object`, null)
        }

        fun setFirst(`object`: Any?, tag: String?) {
            addStack()
            val step = add(`object`)
            if (tag != null) step.tag = tag else if (`object` is Person) step.tag = "INDI"
            //log("setPrimo");
        }

        /**
         * Adds an object to the end of the last existing stack
         */
        @JvmStatic
        fun add(`object`: Any?): Step {
            val step = Step()
            step.obj = `object`
            stepStack.add(step)
            //log("aggiungi");
            return step
        }

        /**
         * Put the first item if there are no stacks or replace the first item in the last existing stack.
         * In other words, it puts the first object without adding any more stacks
         */
        @JvmStatic
        fun replaceFirst(`object`: Any?) {
            val tag = if (`object` is Family) "FAM" else "INDI"
            if (memory.list.size == 0) {
                setFirst(`object`, tag)
            } else {
                stepStack.clear()
                val step = add(`object`)
                step.tag = tag
            }
            //log("replacePrimo");
        }

        /**
         * The object contained in the first step of the stack
         */
        @JvmStatic
        fun firstObject(): Any? {
            return if (stepStack.size > 0) stepStack.firstElement()!!.obj else null
        }

        /**
         * If the stack has more than one object, get the second to last object, otherwise return null
         * The object in the previous step to the last - L'object nel passo precedente all'ultimo
         * I think it was called containerObject()?
         */
        @JvmStatic
        val secondToLastObject: Any?
            get() {
                val stepStack = stepStack
                return if (stepStack.size > 1) stepStack[stepStack.size - 2]!!.obj else null
            }

        /**
         * The object in the last step
         */
        val `object`: Any?
            get() = if (stepStack.size == 0) null else stepStack.peek()!!.obj

        @JvmStatic
        fun clearStackAndRemove() { //lit. retreat
            while (stepStack.size > 0 && stepStack.lastElement()!!.clearStackOnBackPressed) stepStack.pop()
            if (stepStack.size > 0) stepStack.pop()
            if (stepStack.isEmpty()) memory.list.remove(stepStack)
            //log("arretra");
        }

        /**
         * When an object is deleted, make it null in all steps,
         * and the objects in any subsequent steps are also canceled.
         */
        @JvmStatic
        fun setInstanceAndAllSubsequentToNull(subject: Any) {
            for (stepStack in memory.list) {
                var shouldSetSubsequentToNull = false
                /*TODO consider using index instead, to avoid needless reassignment
			    and boolean expression evaluation ("|| shouldSetSubsequentToNull")
			*
			* int index = -1;
			* for (int i = 0; i < stepStack.size(); i++) {
            *     if (step.object != null && step.object.equals(subject)) {
            *         index = i;
            * 		  break;
            *     }
            * }
			* if(index >= 0) {
			*     for(Step step : stepStack.subList(index, stepStack.size) {
			*         step.object = null
			*     }
			* }
			*
			* in Kotlin this would be:
			* val index = stepStack.indexOf { it.object != null && it.object == subject }
			* if(index >= 0) for(step in stepStack.subList(index, stepStack.size) {
			*     step.object = null
			* }
			* */for (step in stepStack) {
                    if ((step?.obj == subject || shouldSetSubsequentToNull)) {
                        step?.obj = null
                        shouldSetSubsequentToNull = true
                    }
                }
            }
        }

        fun log(intro: String?) {
            if (intro != null) s.l(intro)
            for (stepStack in memory.list) {
                for (step in stepStack) {
                    val triplet = if (step?.clearStackOnBackPressed == true) "< " else ""
                    if (step?.tag != null) s.p(triplet + step.tag + " ") else if (step?.obj != null) s.p(
                        triplet + step?.obj!!.javaClass.simpleName + " "
                    ) else s.p(triplet + "Null") // it happens in very rare cases
                }
                s.l("")
            }
            s.l("- - - -")
        }
    }
}