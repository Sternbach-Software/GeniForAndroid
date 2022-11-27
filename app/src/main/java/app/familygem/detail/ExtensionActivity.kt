package app.familygem.detail

import app.familygem.DetailActivity
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import org.folg.gedcom.model.GedcomTag

class ExtensionActivity : DetailActivity() {
    lateinit var e: GedcomTag
    override fun format() {
        title = getString(R.string.extension)
        e = cast(GedcomTag::class.java) as GedcomTag
        placeSlug(e.tag)
        place(getString(R.string.id), "Id", false, false)
        place(getString(R.string.value), "Value", true, true)
        place("Ref", "Ref", false, false)
        place(
            "ParentTagName",
            "ParentTagName",
            false,
            false
        ) // I did not understand if it is used or not //non ho capito se viene usato o no
        for (child in e.children) {
            placePiece(
                child.tag,
                U
                    .traverseExtension(child, 0)
                    .removeSuffix("\n"),
                child,
                true
            )
        }
    }

    override fun delete() {
        U.deleteExtension(e, Memory.secondToLastObject, null)
        U.updateChangeDate(Memory.firstObject())
    }
}