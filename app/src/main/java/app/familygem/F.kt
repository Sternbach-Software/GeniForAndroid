package app.familygem

import android.Manifest
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import app.familygem.detail.ImageActivity
import app.familygem.visitor.MediaList
import com.google.gson.JsonPrimitive
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import com.squareup.picasso.RequestCreator
import com.theartofdev.edmodo.cropper.CropImage
import com.theartofdev.edmodo.cropper.CropImageView
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.MediaContainer
import org.folg.gedcom.model.Person
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.File
import java.net.URL
import java.net.URLConnection

/**
 * Static functions to manage files and media
 */
object F {
    /**
     * Packaging to get a folder in KitKat
     * Impacchettamento per ricavare una cartella in KitKat
     */
    @JvmStatic
    fun uriPathFolderKitKat(context: Context?, uri: Uri?): String? {
        val path = uriFilePath(uri)
        val lastIndexOf = path?.lastIndexOf('/')
        return if (path != null && lastIndexOf!! > 0)
            path.substring(0, lastIndexOf)
        else {
            Toast.makeText(context, "Could not get this position.", Toast.LENGTH_SHORT).show()
            null
        }
    }

    /**
     * It receives a Uri and tries to return the path to the file
     * Version commented in lab
     *
     *
     * Riceve un Uri e cerca di restituire il percorso del file
     * Versione commentata in lab
     */
    @JvmStatic
    fun uriFilePath(uri: Uri?): String? {
        if (uri == null) return null
        if (uri.scheme?.equals("file", ignoreCase = true) == true) {
            // Remove 'file://'
            return uri.path
        }
        when (uri.authority) {
            "com.android.externalstorage.documents" -> {
                val split = uri.lastPathSegment!!.split(":")
                val first = split[0]
                when {
                    first.equals("primary", ignoreCase = true) -> {
                        // Main storage
                        val path = Environment.getExternalStorageDirectory().toString() + "/" + split[1]
                        if (File(path).canRead()) return path
                    }
                    first.equals("home", ignoreCase = true) -> {
                        // 'Documents' folder in Android 9 and 10
                        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                            .toString() + "/" + split[1]
                    }
                    else -> {
                        // All other cases including SD cards
                        val places = Global.context.getExternalFilesDirs(null)
                        for (file in places) {
                            val indexOf = file.absolutePath.indexOf("/Android")
                            if (indexOf > 0) {
                                val found = File(file.absolutePath.substring(0, indexOf), split[1])
                                if (found.canRead()) return found.absolutePath
                            }
                        }
                    }
                }
            }
            "com.android.providers.downloads.documents" -> {
                val id = uri.lastPathSegment
                if (id!!.startsWith("raw:/")) return id.replaceFirst("raw:", "")
                if (id.matches("\\d+".toRegex())) {
                    val contentUriPrefixesToTry = listOf(
                        "content://downloads/public_downloads",
                        "content://downloads/my_downloads"
                    )
                    for (contentUriPrefix in contentUriPrefixesToTry) {
                        val rebuilt =
                            ContentUris.withAppendedId(Uri.parse(contentUriPrefix), id.toLong())
                        try {
                            val filename = findFilename(rebuilt)
                            if (filename != null) return filename
                        } catch (e: Exception) {
                        }
                    }
                }
            }
        }
        return findFilename(uri)
    }

    /**
     * // Get the URI (possibly reconstructed) of a file taken with SAF
     * // If successful, return the full path, otherwise the single file name
     *
     *
     * // Riceve l'URI (eventualmente ricostruito) di un file preso con SAF
     * // Se riesce restituisce il percorso completo, altrimenti il singolo nome del file
     */
    private fun findFilename(uri: Uri): String? {
        val cursor = Global.context.contentResolver.query(uri, null, null, null, null)
        if (cursor?.moveToFirst() == true) {
            var index = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATA)
            if (index < 0) index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val filename = cursor.getString(index)
            cursor.close()
            return filename
        }
        return null
    }

    /**
     * // Receive a Uri tree obtained with ACTION_OPEN_DOCUMENT_TREE and try to return the path of the folder
     * // otherwise it quietly returns null
     *
     *
     * // Riceve un tree Uri ricavato con ACTION_OPEN_DOCUMENT_TREE e cerca di restituire il percorso della cartella
     * // altrimenti tranquillamente restituisce null
     */
    @JvmStatic
    fun uriFolderPath(uri: Uri?): String? {
        if (uri == null) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            when (uri.authority) {
                "com.android.externalstorage.documents" -> {
                    val split = treeDocId.split(":")
                    val first = split[0]
                    // Main storage
                    var path = when {
                        first.equals("primary", ignoreCase = true) -> {
                            Environment.getExternalStorageDirectory().absolutePath
                        }
                        first.equals("home", ignoreCase = true) -> {
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS).absolutePath
                        }
                        else -> {
                            Global
                                .context
                                .getExternalFilesDirs(null)
                                .find { it.absolutePath.contains(first) }
                                ?.absolutePath
                                ?.substringBefore("/Android")
                        }
                    }
                    if (path != null) {
                        val second = split.getOrNull(1)
                        if (second?.isNotEmpty() == true) return "$path/$second"
                    }
                }
                "com.android.providers.downloads.documents" -> {
                    if (treeDocId == "downloads") return Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                    ).absolutePath
                    if (treeDocId.startsWith("raw:/")) return treeDocId.replaceFirst(
                        "raw:",
                        ""
                    )
                }
            }
        }
        return null
    }

    /**
     * Save a document (PDF, GEDCOM, ZIP) with SAF
     */
    @JvmStatic
    fun saveDocument(
        activity: Activity?,
        fragment: Fragment,
        treeId: Int,
        mime: String?,
        ext: String,
        requestCode: Int
    ) {
        //GEDCOM must specify the extension, the others put it according to the mime type // GEDCOM deve esplicitare l'estensione, gli altri la mettono in base al mime type
        //replaces dangerous characters for the Android filesystem that are not replaced by Android itself // rimpiazza caratteri pericolosi per il filesystem di Android che non vengono ripiazzati da Android stesso
        val name = Global
            .settings
            .getTree(treeId)
            .title
            .replace("[$']".toRegex(), "_")
        val extension = if (ext == "ged") ".ged" else ""
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(mime)
            .putExtra(
                Intent.EXTRA_TITLE,
                "$name$extension"
            )
        activity?.startActivityForResult(intent, requestCode) ?: fragment.startActivityForResult(
            intent,
            requestCode
        )
    }
    // Methods for displaying images:
    /**
     * // Receives a Person and chooses the main Media from which to get the image
     * // Riceve una Person e sceglie il Media principale da cui ricavare l'immagine
     */
    @JvmStatic
    fun showMainImageForPerson(gc: Gedcom?, p: Person, img: ImageView): Media? {
        val mediaList = MediaList(gc, 0)
        p.accept(mediaList)
        val media = mediaList
            .list
            .find { it.primary == "Y" } // Look for a media with Primary 'Y'
            ?: mediaList.list.firstOrNull()
        if (media != null) {
            showImage(media, img, null)
            img.visibility = View.VISIBLE
        } else img.visibility = View.GONE
        return media
    }

    /**
     * Show pictures with Picasso
     */
    @JvmStatic
    fun showImage(media: Media, imageView: ImageView, progressBar: ProgressBar?) {
        // Comparator needs the new tree id to search through its folder
        val likely =
            if (imageView.parent?.parent != null) imageView.parent.parent.parent as View else null
        val treeId =
            if (likely?.id == R.id.confronto_nuovo) Global.treeId2 else Global.settings.openTree
        val path = mediaPath(treeId, media)
        val uri: Uri? = if (path == null) mediaUri(treeId, media) else null
        progressBar?.visibility = View.VISIBLE
        imageView.setTag(R.id.tag_file_type, 0)
        if (path != null || uri != null) {
            val creator: RequestCreator =
                if (path != null) Picasso.get().load("file://$path")
                else Picasso.get().load(uri)
            creator.placeholder(R.drawable.image)
                .fit()
                .centerInside()
                .into(imageView, object : Callback {
                    override fun onSuccess() {
                        progressBar?.visibility = View.GONE
                        imageView.setTag(R.id.tag_file_type, 1)
                        imageView.setTag(
                            R.id.tag_path,
                            path
                        ) // 'path' or 'uri' one of the 2 is valid, the other is null
                        imageView.setTag(R.id.tag_uri, uri)
                        // On the Image Detail page reload the options menu to show the Crop command
                        if (imageView.id == R.id.immagine_foto) {
                            if (imageView.context is Activity) // In KitKat it is instance of TintContextWrapper
                                (imageView.context as Activity).invalidateOptionsMenu()
                        }
                    }

                    override fun onError(e: Exception) {
                        //Maybe it's a video to make a thumbnail from
                        var bitmap: Bitmap? = null
                        try { //These thumbnail generators have been nailing lately, so better cover your ass// Ultimamente questi generatori di thumbnail inchiodano, quindi meglio pararsi il culo
                            bitmap = ThumbnailUtils.createVideoThumbnail(
                                path!!,
                                MediaStore.Video.Thumbnails.MINI_KIND
                            )
                            // Via the URI//Tramite l'URI
                            if (bitmap == null && uri != null) {
                                val mMR = MediaMetadataRetriever()
                                mMR.setDataSource(Global.context, uri)
                                bitmap = mMR.frameAtTime
                            }
                        } catch (excpt: Exception) {
                        }
                        imageView.setTag(R.id.tag_file_type, 2)
                        if (bitmap == null) {
                            // A local file with no preview
                            var format = media.format
                                ?: if (path != null) MimeTypeMap.getFileExtensionFromUrl(
                                    path.replace(
                                        "[^a-zA-Z0-9./]".toRegex(),
                                        "_"
                                    )
                                ) else ""
                            // Removes whitespace that does not find the extension
                            if (format.isEmpty() && uri != null) format =
                                MimeTypeMap.getFileExtensionFromUrl(
                                    uri.lastPathSegment
                                )
                            bitmap = generateIcon(imageView, R.layout.media_file, format)
                            imageView.scaleType = ImageView.ScaleType.FIT_CENTER
                            if (imageView.parent is RelativeLayout &&  // ugly but effective
                                (imageView.parent as RelativeLayout).findViewById<View?>(R.id.media_testo) != null
                            ) {
                                val param = RelativeLayout.LayoutParams(
                                    RelativeLayout.LayoutParams.MATCH_PARENT,
                                    RelativeLayout.LayoutParams.MATCH_PARENT
                                )
                                param.addRule(RelativeLayout.ABOVE, R.id.media_testo)
                                imageView.layoutParams = param
                            }
                            imageView.setTag(R.id.tag_file_type, 3)
                        }
                        imageView.setImageBitmap(bitmap)
                        imageView.setTag(R.id.tag_path, path)
                        imageView.setTag(R.id.tag_uri, uri)
                        progressBar?.visibility = View.GONE
                    }
                })
        } else if (media.file?.isNotEmpty() == true) { // maybe it's an image on the internet
            val filePath = media.file
            Picasso.get().load(filePath).fit()
                .placeholder(R.drawable.image).centerInside()
                .into(imageView, object : Callback {
                    override fun onSuccess() {
                        progressBar?.visibility = View.GONE
                        imageView.setTag(R.id.tag_file_type, 1)
                        try {
                            CacheImage(media).execute(URL(filePath))
                        } catch (e: Exception) {
                        }
                    }

                    override fun onError(e: Exception) {
                        // Let's try a web page
                        DownloadImage(imageView, progressBar, media).execute(filePath)
                    }
                })
        } else { // Media without a link to a file
            progressBar?.visibility = View.GONE
            imageView.setImageResource(R.drawable.image)
        }
    }

    /**
     * It receives a Media, looks for the file locally with different path combinations and returns the address
     */
    @JvmStatic
    fun mediaPath(treeId: Int, m: Media): String? {
        if (m.file?.isNotEmpty() == true) {
            val name = m.file.replace("\\", "/")
            // FILE path (the one in gedcom)
            if (File(name).canRead()) return name
            for (dir in Global.settings.getTree(treeId).dirs) {
                // media folder + FILE path
                var test = File("$dir/$name")
                /* Todo Sometimes File.isFile () produces an ANR, like https://stackoverflow.com/questions/224756
                 *  I tried with various non-existent paths, such as the removed SD card, or with absurd characters,
                 *  but they all simply return false.
                 *  Probably the ANR is when the path points to an existing resource but it waits indefinitely. */
                if (test.isFile && test.canRead()) return test.path
                // media folder + name of FILE
                test = File(dir, File(name).name)
                if (test.isFile && test.canRead()) return test.path
            }
            val string = m.getExtension("cache")
            // Sometimes it is String sometimes JsonPrimitive, I don't quite understand why
            if (string != null) {
                val cachePath =
                    if (string is String) string
                    else (string as JsonPrimitive).asString
                if (File(cachePath).isFile) return cachePath
            }
        }
        return null
    }

    /**
     * It receives a [Media], looks for the file locally in any tree-URIs and returns the URI
     */
    @JvmStatic
    fun mediaUri(treeId: Int, m: Media): Uri? {
        if (m.file?.isNotEmpty() == true) {
            // OBJE.FILE is never a Uri, always a path (Windows or Android)
            val filename = File(m.file.replace("\\", "/")).name
            for (uri in Global.settings.getTree(treeId).uris) {
                val documentDir = DocumentFile.fromTreeUri(Global.context, Uri.parse(uri))
                val docFile = documentDir!!.findFile(filename)
                if (docFile?.isFile == true) return docFile.uri
            }
        }
        return null
    }

    fun generateIcon(view: ImageView, icon: Int, text: String?): Bitmap {
        val inflater =
            view.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val inflated = inflater.inflate(icon, null)
        val frameLayout = inflated.findViewById<RelativeLayout>(R.id.icona)
        (frameLayout.findViewById<View>(R.id.icona_testo) as TextView).text = text
        frameLayout.isDrawingCacheEnabled = true
        frameLayout.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        frameLayout.layout(0, 0, frameLayout.measuredWidth, frameLayout.measuredHeight)
        frameLayout.buildDrawingCache(true)
        return frameLayout.drawingCache
    }
    // Methods for image acquisition:
    /**
     * Displays a list of apps for capturing images
     * TODO [code] is magic number
     */
    @JvmStatic
    fun displayMediaAppList(
        context: Context,
        fragment: Fragment?,
        code: Int,
        container: MediaContainer
    ) {
        // Request permission to access device memory
        val perm =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (perm == PackageManager.PERMISSION_DENIED) {
            if (fragment != null) { // Gallery
                fragment.requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    code
                )
            } else ActivityCompat.requestPermissions(
                (context as AppCompatActivity), arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), code
            )
            return
        }
        // Collect useful intents to capture images
        val resolveInfos = mutableListOf<ResolveInfo?>()
        val intents = mutableListOf<Intent>()
        // Cameras
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        for (info in context.packageManager.queryIntentActivities(cameraIntent, 0)) {
            val finalIntent = Intent(cameraIntent)
            finalIntent.component =
                ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            intents.add(finalIntent)
            resolveInfos.add(info)
        }
        // Galleries
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT)
        galleryIntent.type = "image/*"
        val mimeTypes = arrayOf(

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) "image/*"
            else "*/*", // Otherwise KitKat does not see the 'application / *' in Downloads

            "audio/*",
            "video/*",
            "application/*",
            "text/*"
        )
        galleryIntent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        for (info in context.packageManager.queryIntentActivities(galleryIntent, 0)) {
            val finalIntent = Intent(galleryIntent)
            finalIntent.component =
                ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            intents.add(finalIntent)
            resolveInfos.add(info)
        }
        // Empty media
        if (Global.settings.expert && code != 5173) { //except for choosing files in Image // tranne che per la scelta di file in Immagine
            val intent = Intent(context, ImageActivity::class.java)
            val info = context.packageManager.resolveActivity(intent, 0)
            intent.component =
                ComponentName(info!!.activityInfo.packageName, info.activityInfo.name)
            intents.add(intent)
            resolveInfos.add(info)
        }
        AlertDialog.Builder(context).setAdapter(
            createAdapter(context, resolveInfos)
        ) { _: DialogInterface?, id: Int ->
            val intent = intents[id]
            // Set up a Uri in which to put the photo taken by the camera app
            if (intent.action != null && intent.action == MediaStore.ACTION_IMAGE_CAPTURE) {
                val dir = context.getExternalFilesDir(Global.settings.openTree.toString())
                if (!dir!!.exists()) dir.mkdir()
                val photoFile = nextAvailableFileName(dir.absolutePath, "image.jpg")
                Global.pathOfCameraDestination =
                    photoFile.absolutePath // This saves it to retake it after the photo is taken
                val photoUri =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) FileProvider.getUriForFile(
                        context,
                        BuildConfig.APPLICATION_ID + ".provider",
                        photoFile
                    ) else  // KitKat
                        Uri.fromFile(photoFile)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            }
            if (intent.component!!.packageName == BuildConfig.APPLICATION_ID) {
                // Create an empty media
                val med: Media
                if (code == 4173 || code == 2173) { // Simple media
                    med = Media()
                    med.fileTag = "FILE"
                    container.addMedia(med)
                    Memory.add(med)
                } else { // Shared media
                    med = GalleryFragment.newMedia(container)
                    Memory.setFirst(med)
                }
                med.file = ""
                context.startActivity(intent)
                U.save(true, Memory.firstObject())
            } else
                fragment?.startActivityForResult(
                    intent,
                    code
                ) // Thus the result returns to the fragment
                    ?: (context as AppCompatActivity).startActivityForResult(intent, code)
        }.show()
    }

    /**
     * Closely related to the method above
     */
    private fun createAdapter(
        context: Context,
        resolveInfos: List<ResolveInfo?>
    ): ArrayAdapter<ResolveInfo?> {
        return object :
            ArrayAdapter<ResolveInfo?>(context, R.layout.piece_app, R.id.app_title, resolveInfos) {
            override fun getView(position: Int, view1: View?, parent: ViewGroup): View {
                val view = super.getView(position, view1, parent)
                val info = resolveInfos[position]
                val image = view.findViewById<ImageView>(R.id.app_icon)
                val textview = view.findViewById<TextView>(R.id.app_title)
                if (info!!.activityInfo.packageName == BuildConfig.APPLICATION_ID) {
                    image.setImageResource(R.drawable.image)
                    textview.setText(R.string.empty_media)
                } else {
                    image.setImageDrawable(info.loadIcon(context.packageManager))
                    textview.text = info.loadLabel(context.packageManager).toString()
                }
                return view
            }
        }
    }

    /**
     * Save the scanned file and propose to crop it if it is an image
     *
     * @return true if it opens the dialog and therefore the updating of the activity must be blocked
     */
    @JvmStatic
    fun proposeCropping(
        context: Context,
        fragment: Fragment?,
        data: Intent?,
        media: Media
    ): Boolean {
        // Find the path of the image
        var uri: Uri? = null
        var path: String?
        // Content taken with SAF
        if (data?.data != null) {
            uri = data.data
            path = uriFilePath(uri)
        } // Photo from camera app
        else if (Global.pathOfCameraDestination != null) {
            path = Global.pathOfCameraDestination
            Global.pathOfCameraDestination = null // resets it
        } // Nothing usable
        else {
            Toast.makeText(context, R.string.something_wrong, Toast.LENGTH_SHORT).show()
            return false
        }

        // Create the file
        lateinit var fileMedia: File
        if (path != null && path.lastIndexOf('/') > 0) { // if it is a full path to the file and not the root directory
            // Directly point to the file
            fileMedia = File(path)
        } else { // It is just the file name 'myFile.ext' or more rarely null
            // External app storage: /storage/emulated/0/Android/data/APPLICATION_ID/files/12
            val externalFilesDir = context.getExternalFilesDir(Global.settings.openTree.toString())
            try { // We use the URI
                val input = context.contentResolver.openInputStream(uri!!)
                // Todo if the file already exists, do not duplicate it but reuse it: as in [ConfirmationActivity.checkIfShouldCopyFiles]
                if (path == null) { // Null filename, must be created from scratch
                    val type = context.contentResolver.getType(uri)
                    path = (type!!.substringBefore('/') + "."
                            + MimeTypeMap.getSingleton().getExtensionFromMimeType(type))
                }
                fileMedia = nextAvailableFileName(externalFilesDir!!.absolutePath, path)
                FileUtils.copyInputStreamToFile(
                    input,
                    fileMedia
                ) // Create the folder if it doesn't exist
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: context.getString(R.string.something_wrong)
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
        //Adds the folder path in the Tree in preferences // Aggiunge il percorso della cartella nel Tree in preferenze
        if (Global.settings.currentTree.dirs.add(fileMedia.parent)) // true if it added the folder
            Global.settings.save()
        // Set the path found in the Media
        media.file = fileMedia.absolutePath

        // If it is an image it opens the cropping proposal dialog
        val mimeType = URLConnection.guessContentTypeFromName(
            fileMedia.name
        )
        if (mimeType?.startsWith("image/") == true) {
            val imageView = ImageView(context)
            showImage(media, imageView, null)
            Global.croppedMedia =
                media //Media parked waiting to be updated with new file path // Media parcheggiato in attesa di essere aggiornato col nuovo percorso file
            Global.edited =
                false //in order not to trigger the recreate () which in new Android does not bring up the AlertDialog // per non innescare il recreate() che negli Android nuovi non fa comparire l'AlertDialog
            AlertDialog.Builder(context)
                .setView(imageView)
                .setMessage(R.string.want_crop_image)
                .setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int ->
                    cropImage(
                        context,
                        fileMedia,
                        null,
                        fragment
                    )
                }
                .setNeutralButton(R.string.no) { _: DialogInterface?, _: Int ->
                    finishProposeCropping(
                        context,
                        fragment
                    )
                }
                .setOnCancelListener { // click out of the dialog
                    finishProposeCropping(context, fragment)
                }.show()
            val params =
                FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, U.dpToPx(320f))
            imageView.layoutParams =
                params // the size assignment must come AFTER creating the dialog
            return true
        }
        return false
    }

    /**
     * Negative conclusion of the image cropping proposal: simply refresh the page to show the image
     * Conclusione negativa della proposta di ritaglio dell'immagine: aggiorna semplicemente la pagina per mostrare l'immagine
     */
    private fun finishProposeCropping(context: Context?, fragment: Fragment?) {
        when {
            fragment is GalleryFragment -> fragment.recreate()
            context is DetailActivity -> context.refresh()
            context is ProfileActivity -> ((context as AppCompatActivity)
                .supportFragmentManager
                .findFragmentByTag("android:switcher:${R.id.profile_pager}:0") as ProfileMediaFragment)
                .refresh()
        }
        Global.edited = true // to refresh previous pages //per rinfrescare le pagine precedenti
    }

    /**
     * // Start cropping an image with Crop Image
     * // 'file Media' and 'uriMedia': one of the two is valid, the other is null
     *
     * // Avvia il ritaglio di un'immagine con CropImage
     * // 'fileMedia' e 'uriMedia': uno dei due è valido, l'altro è null
     */
    @JvmStatic
    fun cropImage(context: Context, fileMedia: File?, _uriMedia: Uri?, fragment: Fragment?) {
        //Departure // Partenza
        val uriMedia = _uriMedia ?: Uri.fromFile(fileMedia)
        // Destination
        val externalFilesDir = context.getExternalFilesDir(Global.settings.openTree.toString())!!
        if (!externalFilesDir.exists()) externalFilesDir.mkdir()
        val destinationFile =
            if (fileMedia?.absolutePath?.startsWith(externalFilesDir.absolutePath) == true) fileMedia // Files already in the storage folder are overwritten
            else {
                val name = fileMedia?.name // Uri
                    ?: DocumentFile.fromSingleUri(context, uriMedia!!)!!.name
                    ?: ""
                nextAvailableFileName(externalFilesDir.absolutePath, name)
            }
        val intent = CropImage.activity(uriMedia)
            .setOutputUri(Uri.fromFile(destinationFile)) // folder in external memory
            .setGuidelines(CropImageView.Guidelines.OFF)
            .setBorderLineThickness(1f)
            .setBorderCornerThickness(6f)
            .setBorderCornerOffset(-3f)
            .setCropMenuCropButtonTitle(context.getText(R.string.done))
            .getIntent(context)
        fragment?.startActivityForResult(intent, CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE)
            ?: (context as AppCompatActivity).startActivityForResult(
                intent,
                CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE
            )
    }

    /**
     * If a file with that name already exists in that folder, increment it with 1 2 3 ...
     * Se in quella cartella esiste già un file con quel nome lo incrementa con 1 2 3...
     */
    @JvmStatic
    fun nextAvailableFileName(dir: String, name: String): File {
        var file = File(dir, name)
        var increment = 0
        while (file.exists()) {
            increment++
            val indexOfDot = name.lastIndexOf('.')
            file = File(
                dir, name.substring(0, indexOfDot)
                        + increment + name.substring(indexOfDot)
            )
        }
        return file
    }

    /**
     * Ends the cropping procedure of an image
     */
    @JvmStatic
    fun endImageCropping(data: Intent?) {
        val result = CropImage.getActivityResult(data)
        val uri =
            result.uri // e.g. 'file:///storage/emulated/0/Android/data/app.familygem/files/5/anna.webp'
        Picasso.get()
            .invalidate(uri) // clears from the cache any image before clipping that has the same path
        val path = uriFilePath(uri)
        Global.croppedMedia.file = path
    }

    /**
     * Answering all permission requests for Android 6+
     * Risposta a tutte le richieste di permessi per Android 6+
     */
    @JvmStatic
    fun permissionsResult(
        context: Context,
        fragment: Fragment?,
        code: Int,
        permissions: Array<String>,
        grantResults: IntArray,
        container: MediaContainer
    ) {
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            displayMediaAppList(context, fragment, code, container)
        } else {
            val s = permissions[0]
            val permission = s.substringAfterLast('.')
            Toast.makeText(
                context,
                context.getString(R.string.not_granted, permission),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /**
     * Cache an image that can be found on the internet for reuse
     * TODO? maybe it might not even be an asynchronous task but a simple function
     */
    internal class CacheImage(var media: Media) : AsyncTask<URL?, Void?, String?>() {
        @Deprecated("Deprecated in Java")
        override fun doInBackground(vararg url: URL?): String? {
            try {
                val cacheFolder =
                    File(Global.context.cacheDir.path + "/" + Global.settings.openTree)
                if (!cacheFolder.exists()) {
                    // Delete "cache" extension from all Media
                    val mediaList = MediaList(Global.gc, 0)
                    Global.gc.accept(mediaList)
                    for (media in mediaList.list) if (media.getExtension("cache") != null) media.putExtension(
                        "cache",
                        null
                    )
                    cacheFolder.mkdir()
                }
                var extension = FilenameUtils.getName(url[0]?.path)
                val indexOfDot = extension.lastIndexOf('.')
                if (indexOfDot > 0) extension =
                    extension.substring(indexOfDot + 1)
                if (extension == "jpeg" || extension !in arrayOf("png", "gif", "bmp", "jpg")) extension = "jpg" //TODO why should the default be jpg??
                val cache = nextAvailableFileName(cacheFolder.path, "img.$extension")
                FileUtils.copyURLToFile(url[0], cache)
                return cache.path
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        override fun onPostExecute(path: String?) {
            if (path != null) media.putExtension("cache", path)
        }
    }

    /**
     * Asynchronously downloads an image from an internet page
     * TODO passing in [imageView] and [progressBar] is a memory leak! And what if the view is invalidated and you try to set the image?
     */
    internal class DownloadImage(
        val imageView: ImageView,
        val progressBar: ProgressBar?,
        var media: Media
    ) : AsyncTask<String?, Int?, Bitmap?>() {
        private var url: URL? = null

        //TODO magic number
        var fileTypeTag = 0 // setTag must be in the main thread, not in the doInBackground.
        private var imageViewWidth = imageView.width //ditto

        override fun doInBackground(vararg parametri: String?): Bitmap? {
            val bitmap: Bitmap?
            try {
                val connection = Jsoup.connect(parametri[0]!!)
                //if (connection.equals(bitmap)) {	// TODO: verify that an address is associated with the hostname
                val doc = connection.get()
                val list: List<Element> = doc.select("img")
                if (list.isEmpty()) { // Web page found but without images
                    fileTypeTag = 3
                    url = URL(parametri[0])
                    return generateIcon(
                        imageView,
                        R.layout.media_mondo,
                        url!!.protocol
                    ) // returns a bitmap
                }
                var maxDimensionsWithAlt = 1
                var maxDimensions = 1
                var maxLengthAlt = 0
                var maxLengthSrc = 0
                var imgHeightConAlt: Element? = null
                var imgHeight: Element? = null
                var imgLengthAlt: Element? = null
                var imgLengthSrc: Element? = null
                for (img in list) {
                    val width =
                        img.attr("width").let { if (it.isEmpty()) 1 else img.attr("width").toInt() }
                    val height = img.attr("height")
                        .let { if (it.isEmpty()) 1 else img.attr("height").toInt() }
                    val alt = img.attr("alt")
                    val src = img.attr("src")
                    if (width * height > maxDimensionsWithAlt && alt.isNotEmpty()
                    ) {    // the largest with alt
                        imgHeightConAlt = img
                        maxDimensionsWithAlt = width * height
                    }
                    if (width * height > maxDimensions) {    // the largest even without alt
                        imgHeight = img
                        maxDimensions = width * height
                    }
                    if (alt.length > maxLengthAlt) { //the one with the longest alt
                        imgLengthAlt = img
                        maxLengthAlt = alt.length
                    }
                    if (src.length > maxLengthSrc) { // the one with the longest src
                        imgLengthSrc = img
                        maxLengthSrc = src.length
                    }
                }
                val path = imgHeightConAlt?.absUrl("src") //absolute URL on src
                    ?: imgHeight?.absUrl("src")
                    ?: imgLengthAlt?.absUrl("src")
                    ?: imgLengthSrc?.absUrl("src")
                url = URL(path)
                var inputStream = url!!.openConnection().getInputStream()
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds =
                    true // it just takes the info of the image without downloading it
                BitmapFactory.decodeStream(inputStream, null, options)
                // Finally try to load the actual image by resizing it
                if (options.outWidth > imageViewWidth) options.inSampleSize =
                    options.outWidth / (imageViewWidth + 1)
                inputStream = url!!.openConnection().getInputStream()
                options.inJustDecodeBounds = false // Download the image
                bitmap = BitmapFactory.decodeStream(inputStream, null, options)
                fileTypeTag = 1
            } catch (e: Exception) {
                return null
            }
            return bitmap
        }

        @Deprecated("Deprecated in Java")
        override fun onPostExecute(bitmap: Bitmap?) {
            imageView.setTag(R.id.tag_file_type, fileTypeTag)
            if (bitmap != null) {
                if (fileTypeTag == 1) CacheImage(media).execute(url)
                kotlin.runCatching { //not sure this will prevent a crash in case the view is invalid
                    imageView.setImageBitmap(bitmap)
                    imageView.setTag(R.id.tag_path, url.toString()) // used by Image
                }
            }
            runCatching { //not sure this will prevent a crash in case the view is invalid
                progressBar?.visibility = View.GONE
            }
        }
    }

    fun StringBuilder.appendIfNotNull(string: String?, addLine: Boolean = true): StringBuilder =
        if (string != null)
            if (addLine) appendLine(string)
            else append(string)
        else this
}