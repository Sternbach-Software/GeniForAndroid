package app.familygem

import android.content.Context
import app.familygem.F.mediaPath
import app.familygem.F.mediaUri
import org.folg.gedcom.model.Gedcom
import app.familygem.TreesActivity
import app.familygem.R
import org.folg.gedcom.visitors.GedcomWriter
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import app.familygem.Settings.ZippedTree
import app.familygem.F
import app.familygem.NewTreeActivity
import app.familygem.U
import android.provider.OpenableColumns
import app.familygem.constant.intdefs.ALL_MEDIA
import app.familygem.visitor.MediaList
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.Media
import java.io.File
import java.io.IOException
import java.lang.Exception
import java.util.HashMap
import java.util.HashSet
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Utility class to export a tree as GEDCOM or ZIP backup
 */
class Exporter internal constructor(private val context: Context) {
    private var treeId = 0
    private var gc //TODO rename to gedcom
            : Gedcom? = null
    private var targetUri: Uri? = null
    var errorMessage // Message of possible error
            : String? = null
    var successMessage // Message of the obtained result
            : String? = null

    /**
     * Opens the Json tree and returns true if successful
     */
    fun openTree(treeId: Int): Boolean {
        this.treeId = treeId
        gc = TreesActivity.openGedcomTemporarily(treeId, true)
        return if (gc == null) {
            error(R.string.no_useful_data)
        } else true
    }

    /**
     * Writes only GEDCOM in the URI
     * Scrive il solo GEDCOM nell'URI
     */
    fun exportGedcom(targetUri: Uri): Boolean {
        this.targetUri = targetUri
        updateHeader(extractFilename(targetUri))
        optimizeGedcom()
        val writer = GedcomWriter()
        val gedcomFile = File(context.cacheDir, "temp.ged")
        try {
            writer.write(gc, gedcomFile)
            val out = context.contentResolver.openOutputStream(targetUri)
            FileUtils.copyFile(gedcomFile, out)
            out!!.flush()
            out.close()
        } catch (e: Exception) {
            return error(e.localizedMessage)
        }

        // Make the file visible from Windows
        // But it seems ineffective in KitKat where the file remains invisible // Ma pare inefficace in KitKat in cui il file rimane invisibile
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri))
        Global.gc = TreesActivity.readJson(treeId) // Reset the changes
        return success(R.string.gedcom_exported_ok)
    }

    /**
     * Writes the GEDCOM with the media in a ZIP file
     */
    fun exportGedcomToZip(targetUri: Uri?): Boolean {
        this.targetUri = targetUri
        // Create the GEDCOM file
        val title = Global.settings.getTree(treeId)!!.title
        val filename = title.replace("[\\\\/:*?\"<>|'$]".toRegex(), "_") + ".ged"
        updateHeader(filename)
        optimizeGedcom()
        val writer = GedcomWriter()
        val fileGc = File(context.cacheDir, filename)
        try {
            writer.write(gc, fileGc)
        } catch (e: Exception) {
            return error(e.localizedMessage)
        }
        val gedcomDocument = DocumentFile.fromFile(fileGc)
        // Add the GEDCOM to the media file collection
        val collection = collectMedia()
        collection[gedcomDocument] = 0
        if (!createZipFile(collection)) return false
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri))
        Global.gc = TreesActivity.readJson(treeId)
        return success(R.string.zip_exported_ok)
    }

    /**
     * Create a zipped file with the tree, settings and media
     */
    fun exportBackupZip(root: String?, grade: Int, targetUri: Uri?): Boolean {
        var root = root
        var grade = grade
        this.targetUri = targetUri
        // Media
        val files = collectMedia()
        // Tree's json
        val fileTree = File(context.filesDir, "$treeId.json")
        files[DocumentFile.fromFile(fileTree)] = 1
        // Preference's json
        val tree = Global.settings.getTree(treeId)
        if (root == null) root = tree!!.root
        if (grade < 0) grade = tree!!.grade
        // String titleTree, String root, int degree can arrive other than Share // String titoloAlbero, String radice, int grado possono arrivare diversi da Condividi
        val settings = ZippedTree(
            tree!!.title, tree.persons, tree.generations, root!!, tree.shares!!, grade
        )
        val fileSettings = settings.save()
        files[DocumentFile.fromFile(fileSettings)] = 0
        if (!createZipFile(files)) return false
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, targetUri))
        return success(R.string.zip_exported_ok)
    }

    /**
     * Returns the number of media files to attach
     */
    fun numMediaFilesToAttach(): Int {
        val mediaList = MediaList(gc!!, ALL_MEDIA)
        gc!!.accept(mediaList)
        var numFiles = 0
        for (med in mediaList.list) {
            if (mediaPath(treeId, med) != null || mediaUri(treeId, med) != null) numFiles++
        }
        return numFiles
    }

    /**
     * Receives the id of a tree and gets a DocumentFile Map of the media that it manages to find
     *
     *
     * Riceve l'id di un albero e arriva una Map di DocumentFile dei media che riesce a rastrellare
     */
    private fun collectMedia(): MutableMap<DocumentFile?, Int> {
        val mediaList = MediaList(gc!!, ALL_MEDIA)
        gc!!.accept(mediaList)

        /* It happens that different Media point to the same file.
         * And it could also happen that different paths end up with the same filenames,
         * eg. 'pathA / img.jpg' 'pathB / img.jpg'
         * You must avoid that files with the same name end up in the ZIP media.
         * This loop creates a list of paths with unique filenames */

        /*  Capita che diversi Media puntino allo stesso file.
         *   E potrebbe anche capitare che diversi percorsi finiscano con nomi di file uguali,
         *   ad es. 'percorsoA/img.jpg' 'percorsoB/img.jpg'
         *   Bisogna evitare che nei media dello ZIP finiscano file con lo stesso nome.
         *   Questo loop crea una lista di percorsi con nome file univoci */
        val paths: MutableSet<String> = HashSet()
        val onlyFileNames: MutableSet<String> =
            HashSet() // Control file names //Nomi file di controllo
        for (med in mediaList.list) {
            val path = med.file
            if (path != null && !path.isEmpty()) {
                var fileName = path.replace('\\', '/')
                if (fileName.lastIndexOf('/') > -1) fileName =
                    fileName.substring(fileName.lastIndexOf('/') + 1)
                if (!onlyFileNames.contains(fileName)) paths.add(path)
                onlyFileNames.add(fileName)
            }
        }
        val collection: MutableMap<DocumentFile?, Int> = HashMap()
        for (path in paths) {
            val med = Media()
            med.file = path
            // Paths
            val mediaPath = mediaPath(treeId, med)
            if (mediaPath != null) collection[DocumentFile.fromFile(File(mediaPath))] =
                2 // todo canRead() ?
            else { // URIs
                val uriMedia = mediaUri(treeId, med)
                if (uriMedia != null) collection[DocumentFile.fromSingleUri(context, uriMedia)] = 2
            }
        }
        return collection
    }

    private fun updateHeader(gedcomFilename: String?) {
        val header = gc!!.header
        if (header == null) gc!!.header = NewTreeActivity.createHeader(gedcomFilename) else {
            header.file = gedcomFilename
            header.dateTime = U.actualDateTime()
        }
    }

    /**
     * Enhance GEDCOM for export
     */
    fun optimizeGedcom() {
        // Value of names from given and surname
        for (pers in gc!!.people) {
            for (n in pers.names) if (n.value == null && (n.prefix != null || n.given != null || n.surname != null || n.suffix != null)) {
                var epiteto = "" //TODO replace with stringbuilder
                if (n.prefix != null) epiteto = n.prefix
                if (n.given != null) epiteto += " " + n.given
                if (n.surname != null) epiteto += " /" + n.surname + "/"
                if (n.suffix != null) epiteto += " " + n.suffix
                n.value = epiteto.trim { it <= ' ' }
            }
        }
    }

    /**
     * Extracts only the filename from a URI
     */
    private fun extractFilename(uri: Uri): String? {
        // file://
        if (uri.scheme != null && uri.scheme.equals("file", ignoreCase = true)) {
            return uri.lastPathSegment
        }
        // Cursor (this usually works)
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val filename = cursor.getString(index)
            cursor.close()
            if (filename != null) return filename
        }
        // DocumentFile
        val document = DocumentFile.fromSingleUri(context, targetUri!!)
        val filename = document!!.name
        return filename ?: "tree.ged"
        // Not much else to do
    }

    /**
     * Get the list of DocumentFiles and put them in a ZIP file written to the targetUri
     * Return error message or null if all is well
     */
    fun createZipFile(files: Map<DocumentFile?, Int>): Boolean {
        val buffer = ByteArray(128)
        try {
            val zos = ZipOutputStream(
                context.contentResolver.openOutputStream(
                    targetUri!!
                )
            )
            for ((file, value) in files) {
                val input = context.contentResolver.openInputStream(
                    file!!.uri
                )
                var filename =
                    file.name //Files that are not renamed ('settings.json', 'family.ged') // File che non vengono rinominati ('settings.json', 'famiglia.ged')
                if (value == 1) filename = "tree.json" else if (value == 2) filename =
                    "media/" + file.name
                zos.putNextEntry(ZipEntry(filename))
                var read: Int
                while (input!!.read(buffer).also { read = it } != -1) {
                    zos.write(buffer, 0, read)
                }
                zos.closeEntry()
                input.close()
            }
            zos.close()
        } catch (e: IOException) {
            return error(e.localizedMessage)
        }
        return true
    }

    fun success(message: Int): Boolean {
        successMessage = context.getString(message)
        return true
    }

    fun error(error: Int): Boolean {
        return error(context.getString(error))
    }

    fun error(error: String?): Boolean {
        errorMessage = error
        return false
    }
}