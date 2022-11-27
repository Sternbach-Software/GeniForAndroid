package app.familygem.detail

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import app.familygem.*
import app.familygem.F.appendIfNotNull
import app.familygem.constant.intdefs.CITATION_KEY
import app.familygem.visitor.ListOfSourceCitations
import org.folg.gedcom.model.Source

class SourceActivity : DetailActivity() {

    lateinit var f: Source //f for fonte/"source"

    override fun format() {
        setTitle(R.string.source)
        f = cast(Source::class.java) as Source
        placeSlug("SOUR", f.id)
        val citations = ListOfSourceCitations(Global.gc!!, f.id)
        f.putExtension(CITATION_KEY, citations.list.size) // for the LibraryFragment
        place(getString(R.string.abbreviation), "Abbreviation")
        place(getString(R.string.title), "Title", true, true)
        place(getString(R.string.type), "Type", false, true) // _type
        place(getString(R.string.author), "Author", true, true)
        place(getString(R.string.publication_facts), "PublicationFacts", true, true)
        place(getString(R.string.date), "Date") // always null in my Gedcom
        place(getString(R.string.text), "Text", true, true)
        place(
            getString(R.string.call_number),
            "CallNumber",
            false,
            false
        ) // CALN it must be in the SOURCE_REPOSITORY_CITATION
        place(
            getString(R.string.italic),
            "Italic",
            false,
            false
        ) // _italic indicates source title to be in italics ???
        place(
            getString(R.string.media_type),
            "MediaType",
            false,
            false
        ) //MEDI, would be in SOURCE REPOSITORY CITATION // MEDI, sarebbe in SOURCE_REPOSITORY_CITATION
        place(
            getString(R.string.parentheses),
            "Paren",
            false,
            false
        ) // _PAREN indicates source facts are to be enclosed in parentheses
        place(getString(R.string.reference_number), "ReferenceNumber") // ref num false???
        place(getString(R.string.rin), "Rin", false, false)
        place(getString(R.string.user_id), "Uid", false, false)
        placeExtensions(f)
        // Put the quote to the archive //Mette la citazione all'archivio TODO improve translation
        if (f.repositoryRef != null) {
            val refView =
                LayoutInflater.from(this).inflate(R.layout.pezzo_citazione_fonte, box, false)
            box.addView(refView)
            refView.setBackgroundColor(resources.getColor(R.color.repository_citation))
            val repositoryRef = f.repositoryRef
            val repository = repositoryRef.getRepository(Global.gc)
            if (repository != null) {
                (refView.findViewById<View>(R.id.fonte_testo) as TextView).text =
                    repository.name
                (refView.findViewById<View>(R.id.citazione_fonte) as CardView).setCardBackgroundColor(
                    resources.getColor(R.color.repository)
                )
            } else refView.findViewById<View>(R.id.citazione_fonte).visibility = View.GONE
            val stringBuilder = StringBuilder()
                .appendIfNotNull(repositoryRef.value)
                .appendIfNotNull(repositoryRef.callNumber)
                .appendIfNotNull(repositoryRef.mediaType, false)

            val textView = refView.findViewById<TextView>(R.id.citazione_testo)
            if (stringBuilder.isEmpty()) textView.visibility = View.GONE
            else textView.text = stringBuilder
            U.placeNotes(
                refView.findViewById<View>(R.id.citazione_note) as LinearLayout,
                repositoryRef,
                false
            )
            refView.setOnClickListener { v: View? ->
                Memory.add(repositoryRef)
                startActivity(Intent(this@SourceActivity, RepositoryRefActivity::class.java))
            }
            registerForContextMenu(refView)
            refView.setTag(R.id.tag_object, repositoryRef) // for the context menu
        }
        U.placeNotes(box, f, true)
        U.placeMedia(box, f, true)
        U.placeChangeDate(box, f.change)
        if (citations.list.isNotEmpty()) U.putContainer(box, citations.progenitors, R.string.cited_by)
    }

    override fun delete() {
        U.updateChangeDate(*SourcesFragment.deleteSource(f))
    }
}