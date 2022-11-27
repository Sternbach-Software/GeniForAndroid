package app.familygem.detail

import org.folg.gedcom.model.RepositoryRef
import app.familygem.detail.RepositoryRefActivity
import android.widget.LinearLayout
import android.view.LayoutInflater
import android.widget.TextView
import androidx.cardview.widget.CardView
import android.content.Intent
import android.view.View
import app.familygem.*
import app.familygem.detail.RepositoryActivity
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.Source

class RepositoryRefActivity : DetailActivity() {
    
    lateinit var r: RepositoryRef
    
    override fun format() {
        placeSlug("REPO")
        r = cast(RepositoryRef::class.java) as RepositoryRef
        if (r.getRepository(Global.gc) != null) { // valid
            setTitle(R.string.repository_citation)
            val repositoryCard = putRepository(box, r.getRepository(Global.gc))
            repositoryCard.setTag(
                R.id.tag_object,
                r.getRepository(Global.gc)
            ) //for the context menu TODO still needed?
            registerForContextMenu(repositoryCard)
        } else if (r.ref != null) { // of a non-existent archive (perhaps deleted) //di un archivio inesistente (magari eliminato)
            setTitle(R.string.inexistent_repository_citation)
        } else { // without ref??
            setTitle(R.string.repository_note)
        }
        place(getString(R.string.value), "Value", false, true)
        place(getString(R.string.call_number), "CallNumber")
        place(getString(R.string.media_type), "MediaType")
        placeExtensions(r)
        U.placeNotes(box, r, true)
    }

    override fun delete() {
        // Delete the citation from the archive and update the date of the source that contained it
        val container = Memory.secondToLastObject as Source
        container.repositoryRef = null
        U.updateChangeDate(container)
        Memory.setInstanceAndAllSubsequentToNull(r)
    }

    companion object {
        @JvmStatic
        fun putRepository(container: LinearLayout, repo: Repository): View {
            val context = container.context
            val repositoryCard =
                LayoutInflater.from(context).inflate(R.layout.pezzo_fonte, container, false)
            container.addView(repositoryCard)
            (repositoryCard.findViewById<View>(R.id.fonte_testo) as TextView).text = repo.name
            (repositoryCard as CardView).setCardBackgroundColor(context.resources.getColor(R.color.repository))
            repositoryCard.setOnClickListener { v: View? ->
                Memory.setFirst(repo)
                context.startActivity(Intent(context, RepositoryActivity::class.java))
            }
            return repositoryCard
        }
    }
}