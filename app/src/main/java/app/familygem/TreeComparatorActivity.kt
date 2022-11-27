package app.familygem

import app.familygem.Comparison.getFront
import app.familygem.DetailActivity.Companion.writeAddress
import app.familygem.F.showImage
import app.familygem.F.showMainImageForPerson
import app.familygem.BaseActivity
import android.os.Bundle
import app.familygem.R
import androidx.appcompat.content.res.AppCompatResources
import android.view.ViewGroup
import androidx.cardview.widget.CardView
import app.familygem.DetailActivity
import app.familygem.F
import app.familygem.U
import android.content.Intent
import android.util.TypedValue
import android.view.MenuItem
import android.view.View
import android.widget.*
import app.familygem.ConfirmationActivity
import app.familygem.TreeComparatorActivity
import app.familygem.constant.intdefs.POSITION_KEY
import org.folg.gedcom.model.*

/**
 * Activity to evaluate a record of the imported tree, with possible comparison with the corresponding record of the old tree
 */
class TreeComparatorActivity : BaseActivity() {
    var clazz // the ruling class of the activity
            : Class<*>? = null
    var destiny = 0
    override fun onCreate(bandolo: Bundle?) {
        super.onCreate(bandolo)
        setContentView(R.layout.confronto)
        if (Comparison.list.size > 0) {
            val max: Int
            val position: Int
            if (Comparison.autoContinue) {
                max = Comparison.numChoices
                position = Comparison.choicesMade
            } else {
                max = Comparison.list.size
                position = intent.getIntExtra(POSITION_KEY, 0)
            }
            val progressBar = findViewById<ProgressBar>(R.id.confronto_progresso)
            progressBar.max = max
            progressBar.progress = position
            (findViewById<View>(R.id.confronto_stato) as TextView).text = "$position/$max"
            val o = getFront(this).object1
            val o2 = getFront(this).object2
            clazz = o?.javaClass ?: o2!!.javaClass
            setupCard(Global.gc, R.id.confronto_vecchio, o)
            setupCard(Global.gc2, R.id.confronto_nuovo, o2)
            destiny = 2
            val okButton = findViewById<Button>(R.id.confronto_bottone_ok)
            okButton.background =
                AppCompatResources.getDrawable(applicationContext, R.drawable.frecciona)
            if (o == null) {
                destiny = 1
                okButton.setText(R.string.add)
                okButton.setBackgroundColor(-0xff2300) // getResources().getColor(R.color.evidenzia)
                okButton.height =
                    30 // ineffective TODO this does not meet Material design guidelines for 48x48 dp touch targets
            } else if (o2 == null) {
                destiny = 3
                okButton.setText(R.string.delete)
                okButton.setBackgroundColor(-0x10000)
            } else if (getFront(this).canBothAddAndReplace) {
                // Another Add button
                val addButton = Button(this)
                addButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                addButton.setTextColor(-0x1)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                params.rightMargin = 15
                params.weight = 3f
                addButton.layoutParams = params
                addButton.setText(R.string.add)
                addButton.setBackgroundColor(-0xff2300)
                addButton.setOnClickListener { v: View? ->
                    getFront(this).destiny = 1
                    continueToNext()
                }
                (findViewById<View>(R.id.confronto_bottoni) as LinearLayout).addView(addButton, 1)
            }

            // Continues automatically if there is no double action to choose
            if (Comparison.autoContinue && !getFront(this).canBothAddAndReplace) {
                getFront(this).destiny = destiny
                continueToNext()
            }

            // Button to accept the new
            okButton.setOnClickListener { vista: View? ->
                getFront(this).destiny = destiny
                continueToNext()
            }
            findViewById<View>(R.id.confronto_bottone_ignora).setOnClickListener { v: View? ->
                getFront(this).destiny = 0
                continueToNext()
            }
        } else onBackPressed() // Return to Compare
    }

    fun setupCard(gc: Gedcom?, cardId: Int, o: Any?) {
        var tit: String? = ""
        var txt: String? = "" //TODO give better names
        var data = ""
        val card = findViewById<CardView>(cardId)
        val imageView = card.findViewById<ImageView>(R.id.confronto_foto)
        if (o is Note) {
            setRecordTypeTextTo(R.string.shared_note)
            val n = o
            txt = n.value
            data = dateHour(n.change)
        } else if (o is Submitter) {
            setRecordTypeTextTo(R.string.submitter)
            val s = o
            tit = s.name
            if (s.email != null) txt += """
     ${s.email}
     
     """.trimIndent()
            if (s.address != null) txt += writeAddress(s.address, true)
            data = dateHour(s.change)
        } else if (o is Repository) {
            setRecordTypeTextTo(R.string.repository)
            val r = o
            tit = r.name
            if (r.address != null) txt += """
     ${writeAddress(r.address, true)}
     
     """.trimIndent()
            if (r.email != null) txt += r.email
            data = dateHour(r.change)
        } else if (o is Media) {
            setRecordTypeTextTo(R.string.shared_media)
            val m = o
            if (m.title != null) tit = m.title
            txt = m.file
            data = dateHour(m.change)
            imageView.visibility = View.VISIBLE
            showImage(m, imageView, null)
        } else if (o is Source) {
            setRecordTypeTextTo(R.string.source)
            val f = o
            if (f.title != null) tit = f.title else if (f.abbreviation != null) tit = f.abbreviation
            if (f.author != null) txt = """
     ${f.author}
     
     """.trimIndent()
            if (f.publicationFacts != null) txt += """
     ${f.publicationFacts}
     
     """.trimIndent()
            if (f.text != null) txt += f.text
            data = dateHour(f.change)
        } else if (o is Person) {
            setRecordTypeTextTo(R.string.person)
            val p = o
            tit = U.properName(p)
            txt = U.details(p, null)
            data = dateHour(p.change)
            imageView.visibility = View.VISIBLE
            showMainImageForPerson(gc!!, p, imageView)
        } else if (o is Family) {
            setRecordTypeTextTo(R.string.family)
            val f = o
            txt = U.familyText(this, gc, f, false)
            data = dateHour(f.change)
        }
        val titleText = card.findViewById<TextView>(R.id.confronto_titolo)
        if (tit == null || tit.isEmpty()) titleText.visibility = View.GONE else titleText.text = tit
        val textTextView = card.findViewById<TextView>(R.id.confronto_testo)
        if (txt!!.isEmpty()) textTextView.visibility = View.GONE else {
            if (txt.endsWith("\n")) txt = txt.substring(0, txt.length - 1)
            textTextView.text = txt
        }
        val changesView = card.findViewById<View>(R.id.confronto_data)
        if (data.isEmpty()) changesView.visibility = View.GONE else (changesView.findViewById<View>(
            R.id.cambi_testo
        ) as TextView).text = data
        if (cardId == R.id.confronto_nuovo) {
            card.setCardBackgroundColor(resources.getColor(R.color.accent_medium))
        }
        if (tit!!.isEmpty() && txt.isEmpty() && data.isEmpty()) // todo do you mean object null?
            card.visibility = View.GONE
    }

    /**
     * Page title.
     */
    fun setRecordTypeTextTo(string: Int) {
        val typeText = findViewById<TextView>(R.id.confronto_tipo)
        typeText.text = getString(string)
    }

    fun dateHour(change: Change?): String {
        var dateHour = ""
        if (change != null) dateHour = change.dateTime.value + " - " + change.dateTime.time
        return dateHour
    }

    fun continueToNext() {
        val intent = Intent()
        if (getIntent().getIntExtra(POSITION_KEY, 0) == Comparison.list.size) {
            // The comparisons are over
            intent.setClass(this, ConfirmationActivity::class.java)
        } else {
            // Next comparison
            intent.setClass(this, TreeComparatorActivity::class.java)
            intent.putExtra(POSITION_KEY, getIntent().getIntExtra(POSITION_KEY, 0) + 1)
        }
        if (Comparison.autoContinue) {
            if (getFront(this).canBothAddAndReplace) Comparison.choicesMade++ else finish() // removes the current front from the stack
        }
        startActivity(intent)
    }

    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        onBackPressed()
        return true
    }

    override fun onBackPressed() {
        super.onBackPressed()
        if (Comparison.autoContinue) Comparison.choicesMade--
    }
}