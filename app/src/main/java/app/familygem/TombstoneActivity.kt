package app.familygem

import app.familygem.BaseActivity
import android.os.Bundle
import app.familygem.R
import android.widget.TextView
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.view.View

class TombstoneActivity : BaseActivity() {
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.lapide)
        val version = findViewById<TextView>(R.id.lapide_versione)
        version.text = getString(R.string.version_name, BuildConfig.VERSION_NAME)
        val link = findViewById<TextView>(R.id.lapide_link)
        //TODO replace with LinkMovementMethod and (or?) LinkifyCompat.addLinks()
        link.paintFlags = link.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        link.setOnClickListener { v: View? ->
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.familygem.app"))
            )
        }
    }
}