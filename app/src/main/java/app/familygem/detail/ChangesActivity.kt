package app.familygem.detail

import android.view.Menu
import app.familygem.DetailActivity
import app.familygem.R
import app.familygem.U
import org.folg.gedcom.model.Change

class ChangesActivity : DetailActivity() {

    lateinit var c: Change

    override fun format() {
        setTitle(R.string.change_date)
        placeSlug("CHAN")
        c = cast(Change::class.java) as Change
        val dateTime = c.dateTime
        if (dateTime != null) {
            if (dateTime.value != null) U.place(box, getString(R.string.value), dateTime.value)
            if (dateTime.time != null) U.place(box, getString(R.string.time), dateTime.time)
        }
        placeExtensions(c)
        U.placeNotes(box, c, true)
    }

    // You don't need a menu here
    override fun onCreateOptionsMenu(m: Menu): Boolean {
        return false
    }
}