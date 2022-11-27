package app.familygem.visitor

/**
 * Closely connected to [FindStack], locate objects to keep in the stack
 */
class CleanStack(  //scopo: scope, object, goal, aim, etc.
    private val scope: Any
) : TotalVisitor() {
    var toDelete = true
    public override fun visit(
        obj: Any,
        isProgenitor: Boolean
    ): Boolean { // the boolean is unused here
        if (obj == scope) toDelete = false
        return true
    }
}