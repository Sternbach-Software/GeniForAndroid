package app.familygem.detail

import app.familygem.visitor.MediaReferences
import android.app.Activity
import android.view.LayoutInflater
import android.os.Build
import androidx.core.content.FileProvider
import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import android.view.View
import android.widget.ImageView
import androidx.multidex.BuildConfig
import app.familygem.*
import org.folg.gedcom.model.Media
import java.io.File
import java.lang.Exception

class ImageActivity : DetailActivity() {

    lateinit var m: Media
    lateinit var imageView: View

    override fun format() {
        m = cast(Media::class.java) as Media
        if (m.id != null) {
            setTitle(R.string.shared_media)
            placeSlug(
                "OBJE",
                m.id
            ) // 'O1' for Multimedia Records only//'O1' solo per Multimedia Records
        } else {
            setTitle(R.string.media)
            placeSlug("OBJE", null)
        }
        displayMedia(m, box.childCount)
        place(getString(R.string.title), "Title")
        place(getString(R.string.type), "Type", false, false) // _type
        if (Global.settings.expert) place(
            getString(R.string.file),
            "File"
        ) // 'Angelina Guadagnoli.jpg' visible only to experts //'Angelina Guadagnoli.jpg' visibile solo agli esperti
        // TODO should be max 259 characters
        place(getString(R.string.format), "Format", Global.settings.expert, false) // jpeg
        place(getString(R.string.primary), "Primary") // _prim
        place(
            getString(R.string.scrapbook),
            "Scrapbook",
            false,
            false
        ) // _scbk the multimedia object should be in the scrapbook
        place(getString(R.string.slideshow), "SlideShow", false, false) //
        place(getString(R.string.blob), "Blob", false, true)
        //s.l( m.getFileTag() );	// FILE o _FILE
        placeExtensions(m)
        U.placeNotes(box, m, true)
        U.placeChangeDate(box, m.change)
        // List of records in which the media is used
        val mediaReferences = MediaReferences(Global.gc, m, false)
        if (mediaReferences.founders.size > 0) U.putContainer(
            box,
            mediaReferences.founders.toTypedArray(), //TODO refactor to use List
            R.string.used_by
        ) else if ((box.context as Activity).intent.getBooleanExtra(
                "daSolo",
                false
            )
        ) U.putContainer(box, Memory.firstObject(), R.string.into)
    }

    private fun displayMedia(m: Media?, position: Int) {
        imageView = LayoutInflater.from(this).inflate(R.layout.immagine_immagine, box, false)
        box.addView(imageView, position)
        val subImageView = imageView.findViewById<ImageView>(R.id.immagine_foto) //TODO rename
        F.showImage(m, subImageView, imageView.findViewById(R.id.immagine_circolo))
        imageView.setOnClickListener { _: View? ->
            val path = subImageView.getTag(R.id.tag_path) as String?
            var uri = subImageView.getTag(R.id.tag_uri) as Uri?
            when (subImageView.getTag(R.id.tag_file_type) as Int) {
                0 -> {    // The file is to be found //Il file è da trovare
                    F.displayMediaAppList(this, null, 5173, null)
                }
                2, 3 -> { // Open files with another app //Apre file con altra app
                    // TODO if the type is 3 but it is a url (web page without images) try to open it as a file: //
                    if (path != null) {
                        val file = File(path)
                        uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                            && path.startsWith(getExternalFilesDir(null)!!.path)
                        ) // An app can be a file provider of only ITS folders
                            FileProvider.getUriForFile(
                                this,
                                BuildConfig.APPLICATION_ID + ".provider",
                                file
                            ) else  // KitKat and all other folders //KitKat e tutte le altre cartelle
                            Uri.fromFile(file)
                    }
                    val mimeType = contentResolver.getType(uri!!)
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, mimeType)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // It is for app properties folders (provider) //Serve per le cartelle di proprietà dell'app (provider)
                    val resolvers = packageManager.queryIntentActivities(intent, 0)
                    // for an extension like .tex that found the mime type, there is no default app //per un'estensione come .tex di cui ha trovato il tipo mime, non c'è nessuna app predefinita
                    if (mimeType == null || resolvers.isEmpty()) {
                        intent.setDataAndType(
                            uri,
                            "*/*"
                        ) // Brutta lista di app generiche //Brutta lista di app generiche
                    }
                    // From android 7 (Nougat api 24) uri file: // are banned in favor of uri content: // so it can't open files
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // ok works in the emulator with Android 9 //ok funziona nell'emulatore con Android 9
                        try {
                            StrictMode::class.java.getMethod("disableDeathOnFileUriExposure")
                                .invoke(null) //TODO don't use reflection to use functions you shouldn't!
                        } catch (e: Exception) {
                        }
                    }
                    startActivity(intent)
                }
                else -> { // Real image //Immagine vera e propria
                    val intent = Intent(this@ImageActivity, BlackboardActivity::class.java)
                    intent.putExtra("path", path)
                    if (uri != null) intent.putExtra("uri", uri.toString())
                    startActivity(intent)
                }
            }
        }
        imageView.setTag(
            R.id.tag_object,
            43614 /*TODO Magic Number*/
        ) // for its context menu //per il suo menu contestuale
        registerForContextMenu(imageView)
    }

    fun updateImage() {
        val position = box.indexOfChild(imageView)
        box.removeView(imageView)
        displayMedia(m, position)
    }

    override fun delete() {
        U.updateChangeDate(*GalleryFragment.deleteMedia(m, null))
    }
}