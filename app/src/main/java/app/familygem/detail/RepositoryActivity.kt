package app.familygem.detail

import app.familygem.DetailActivity
import app.familygem.Global
import app.familygem.R
import app.familygem.U
import app.familygem.list.RepositoriesFragment
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.Source
import java.util.ArrayList

class RepositoryActivity : DetailActivity() {
    
    lateinit var a: Repository
    override fun format() {
        setTitle(R.string.repository)
        a = cast(Repository::class.java) as Repository
        placeSlug("REPO", a.id)
        place(
            getString(R.string.value),
            "Value",
            false,
            true
        ) // Not very standard Gedcom //Non molto Gedcom standard
        place(getString(R.string.name), "Name")
        place(getString(R.string.address), a.address)
        place(getString(R.string.www), "Www")
        place(getString(R.string.email), "Email")
        place(getString(R.string.telephone), "Phone")
        place(getString(R.string.fax), "Fax")
        place(getString(R.string.rin), "Rin", false, false)
        placeExtensions(a)
        U.placeNotes(box, a, true)
        U.placeChangeDate(box, a.change)

        // Collects and displays the sources citing this Repository //Raccoglie e mostra le fonti che citano questo Repository
        val citingSources: MutableList<Source> = ArrayList()
        for (source in Global.gc.sources) if (source?.repositoryRef?.ref == a.id) citingSources.add(
            source
        )
        if (citingSources.isNotEmpty()) U.putContainer(
            box,
            citingSources.toTypedArray(),
            R.string.sources
        )
    }

    override fun delete() {
        U.updateChangeDate(*RepositoriesFragment.delete(a))
    }
}