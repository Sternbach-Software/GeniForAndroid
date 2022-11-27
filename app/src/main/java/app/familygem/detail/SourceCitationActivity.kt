package app.familygem.detail

import app.familygem.*
import org.folg.gedcom.model.Note
import org.folg.gedcom.model.SourceCitation
import org.folg.gedcom.model.SourceCitationContainer

class SourceCitationActivity : DetailActivity() {

    lateinit var c: SourceCitation

    override fun format() {
        placeSlug("SOUR")
        c = cast(SourceCitation::class.java) as SourceCitation
        val source = c.getSource(Global.gc)
        if (source != null) {  // valid source CITATION
            setTitle(R.string.source_citation)
            U.placeSource(box, source, true)
        } else if (c.ref != null) {  // source CITATION of a non-existent source (perhaps deleted)
            setTitle(R.string.inexistent_source_citation)
        } else {    // source NOTE
            setTitle(R.string.source_note)
            place(getString(R.string.value), "Value", true, true)
        }
        place(getString(R.string.page), "Page", true, true)
        place(getString(R.string.date), "Date")
        place(
            getString(R.string.text),
            "Text",
            true,
            true
        ) // applies to both sourceNote and sourceCitation
        //c.getTextOrValue();	practically useless
        //if( c.getDataTagContents() != null )
        //	U.place( box, "Data Tag Contents", c.getDataTagContents().toString() );	// COMBINED DATA TEXT
        place(getString(R.string.certainty), "Quality") // a number from 0 to 3
        //place( "Ref", "Ref", false, false ); // the id of the source
        placeExtensions(c)
        U.placeNotes(box, c, true)
        U.placeMedia(box, c, true)
    }

    override fun delete() {
        val container = Memory.secondToLastObject
        if (container is Note) // Note doesn't extend SourceCitationContainer
            container.sourceCitations.remove(c)
        else
            (container as SourceCitationContainer).sourceCitations.remove(c)
        U.updateChangeDate(Memory.firstObject())
        Memory.setInstanceAndAllSubsequentToNull(c)
    }
}