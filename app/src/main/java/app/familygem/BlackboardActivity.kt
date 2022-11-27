package app.familygem

import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import app.familygem.R
import app.familygem.constant.intdefs.PATH_KEY
import app.familygem.constant.intdefs.URI_KEY
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import java.io.File

class BlackboardActivity : AppCompatActivity() {
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        setContentView(R.layout.lavagna)
        // Show the file in full resolution
        val path = intent.getStringExtra(PATH_KEY)
        val picasso = Picasso.get()
        val creator = if (path != null) {
            picasso.load(File(path))
        } else {
            val uri = Uri.parse(intent.getStringExtra(URI_KEY))
            picasso.load(uri)
        }
        creator.into(findViewById<View>(R.id.lavagna_immagine) as ImageView)
    }
}