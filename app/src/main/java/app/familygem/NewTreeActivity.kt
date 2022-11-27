package app.familygem

import android.Manifest
import app.familygem.FacadeActivity.Companion.downloadShared
import app.familygem.F.uriFilePath
import android.os.Bundle
import android.os.Build
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.EditText
import android.view.inputmethod.EditorInfo
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import android.app.DownloadManager
import android.app.Activity
import android.content.*
import android.net.Uri
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import org.folg.gedcom.parser.ModelParser
import com.google.gson.Gson
import app.familygem.Settings.ZippedTree
import app.familygem.constant.intdefs.DATA_ID_KEY
import app.familygem.constant.intdefs.TREE_2_ID_KEY
import app.familygem.constant.intdefs.TREE_ID_KEY
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.*
import org.folg.gedcom.parser.JsonParser
import java.io.*
import java.lang.Exception
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class NewTreeActivity : BaseActivity() {
    var wheel: View? = null
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.new_tree)
        wheel = findViewById(R.id.new_progress)
        val referrer = Global.settings!!.referrer // Dataid from a share
        val existingDataId = referrer != null && referrer.matches("[0-9]{14}".toRegex())

        // Download the shared tree
        val downloadShared = findViewById<Button>(R.id.new_download_shared)
        if (existingDataId) // It doesn't need any permissions because it only downloads and unpacks to the app's external storage
            downloadShared.setOnClickListener { v: View? ->
                downloadShared(
                    this,
                    referrer,
                    wheel
                )
            } else downloadShared.visibility = View.GONE

        // Create an empty tree
        val emptyTree = findViewById<Button>(R.id.new_empty_tree)
        if (existingDataId) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) emptyTree.backgroundTintList =
                ColorStateList.valueOf(
                    resources.getColor(R.color.primary_light)
                )
        }
        emptyTree.setOnClickListener { v: View? ->
            val messageView = LayoutInflater.from(this).inflate(R.layout.albero_nomina, null)
            val builder = AlertDialog.Builder(this)
            builder.setView(messageView).setTitle(R.string.title)
            val textView = messageView.findViewById<TextView>(R.id.nuovo_nome_testo)
            textView.setText(R.string.modify_later)
            textView.visibility = View.VISIBLE
            val newName = messageView.findViewById<EditText>(R.id.nuovo_nome_albero)
            builder.setPositiveButton(R.string.create) { dialog: DialogInterface?, id: Int ->
                newTree(
                    newName.text.toString()
                )
            }
                .setNeutralButton(R.string.cancel, null).create().show()
            newName.setOnEditorActionListener { view: TextView?, action: Int, event: KeyEvent? ->
                if (action == EditorInfo.IME_ACTION_DONE) {
                    newTree(newName.text.toString())
                    return@setOnEditorActionListener true // complete save() actions
                }
                false // Any other actions that do not exist
            }
            messageView.postDelayed({
                newName.requestFocus()
                val inputMethodManager =
                    getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                inputMethodManager.showSoftInput(newName, InputMethodManager.SHOW_IMPLICIT)
            }, 300)
        }
        val downloadExample = findViewById<Button>(R.id.new_download_example)
        // It doesn't need permissions
        downloadExample.setOnClickListener { v: View? -> downloadExample() }
        val importGedcom = findViewById<Button>(R.id.new_import_gedcom)
        importGedcom.setOnClickListener { v: View ->
            val perm = ContextCompat.checkSelfPermission(
                v.context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
            if (perm == PackageManager.PERMISSION_DENIED) ActivityCompat.requestPermissions(
                (v.context as AppCompatActivity), arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 1390
            ) else if (perm == PackageManager.PERMISSION_GRANTED) importGedcom()
        }
        val fetchBackup = findViewById<Button>(R.id.new_recover_backup)
        fetchBackup.setOnClickListener { v: View? ->
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "application/zip"
            startActivityForResult(intent, 219)
        }
    }

    /**
     * Process response to permit requests
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) { // If request is cancelled, the result arrays are empty
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (requestCode == 1390) {
                importGedcom()
            }
        }
    }

    /**
     * Create a brand new tree
     */
    fun newTree(title: String?) {
        val num = Global.settings!!.max() + 1
        val jsonFile = File(filesDir, "$num.json")
        Global.gc = Gedcom()
        Global.gc!!.header = createHeader(jsonFile.name)
        Global.gc!!.createIndexes()
        val jp = JsonParser()
        try {
            FileUtils.writeStringToFile(jsonFile, jp.toJson(Global.gc), "UTF-8")
        } catch (e: Exception) {
            Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
            return
        }
        Global.settings!!.add(Settings.Tree(num, title!!, null, 0, 0, null, null, 0))
        Global.settings!!.openTree = num
        Global.settings!!.save()
        onBackPressed()
        Toast.makeText(this, R.string.tree_created, Toast.LENGTH_SHORT).show()
    }

    /**
     * Download the Simpsons zip file from Google Drive to the external cache of the app, therefore without the need for permissions
     */
    fun downloadExample() {
        wheel!!.visibility = View.VISIBLE
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        // Avoid multiple downloads
        val cursor = downloadManager.query(
            DownloadManager.Query().setFilterByStatus(DownloadManager.STATUS_RUNNING)
        )
        if (cursor.moveToFirst()) {
            cursor.close()
            findViewById<View>(R.id.new_download_example).isEnabled = false
            return
        }
        val url = "https://drive.google.com/uc?export=download&id=1FT-60avkxrHv6G62pxXs9S6Liv5WkkKf"
        val zipPath = externalCacheDir.toString() + "/the_Simpsons.zip"
        val fileZip = File(zipPath)
        if (fileZip.exists()) fileZip.delete()
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle(getString(R.string.simpsons_tree))
            .setDescription(getString(R.string.family_gem_example))
            .setMimeType("application/zip")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.parse("file://$zipPath"))
        downloadManager.enqueue(request)
        val onComplete: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(contesto: Context, intent: Intent) {
                unZip(contesto, zipPath, null)
                unregisterReceiver(this)
            }
        }
        registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        // ACTION_DOWNLOAD_COMPLETE means the completion of ANY download that is in progress, not just this one.
    }

    /**
     * Choose a Gedcom file to import
     */
    fun importGedcom() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        // KitKat disables .ged files in Download folder if type is 'application/*'
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) intent.type =
            "*/*" else intent.type = "application/*"
        startActivityForResult(intent, 630)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Import a chosen Gedcom file with SAF
        if (resultCode == RESULT_OK && requestCode == 630) {
            try {
                // Read the input
                val uri = data!!.data
                val input = contentResolver.openInputStream(uri!!)
                val gedcom = ModelParser().parseGedcom(input)
                if (gedcom.header == null) {
                    Toast.makeText(this, R.string.invalid_gedcom, Toast.LENGTH_LONG).show()
                    return
                }
                gedcom.createIndexes() // necessary to then calculate the generations
                // Save the json file
                val newNumber = Global.settings!!.max() + 1
                val printWriter = PrintWriter("$filesDir/$newNumber.json")
                val jsonParser = JsonParser()
                printWriter.print(jsonParser.toJson(gedcom))
                printWriter.close()
                // Folder tree name and path
                val path = uriFilePath(uri)
                var treeName: String
                var folderPath: String? = null
                if (path != null && path.lastIndexOf('/') > 0) { // is a full path to the gedcom file
                    val fileGedcom = File(path)
                    folderPath = fileGedcom.parent
                    treeName = fileGedcom.name
                } else if (path != null) { // It's just the name of the file 'family.ged'
                    treeName = path
                } else  // null path
                    treeName = getString(R.string.tree) + " " + newNumber
                if (treeName.lastIndexOf('.') > 0) // Remove the extension
                    treeName = treeName.substring(0, treeName.lastIndexOf('.'))
                // Save the settings in preferences
                val idRadice = U.findRoot(gedcom)
                Global.settings!!.add(
                    Settings.Tree(
                        newNumber,
                        treeName,
                        folderPath,
                        gedcom.people.size,
                        TreeInfoActivity.countGenerations(gedcom, idRadice),
                        idRadice,
                        null,
                        0
                    )
                )
                Notifier(this, gedcom, newNumber, Notifier.What.CREATE)
                // If necessary it proposes to show the advanced functions
                if (!gedcom.sources.isEmpty() && !Global.settings!!.expert) {
                    AlertDialog.Builder(this).setMessage(R.string.complex_tree_advanced_tools)
                        .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, i: Int ->
                            Global.settings!!.expert = true
                            Global.settings!!.save()
                            finishImportingGedcom()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog: DialogInterface?, i: Int -> finishImportingGedcom() }
                        .show()
                } else finishImportingGedcom()
            } catch (e: Exception) {
                Toast.makeText(this, e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }

        // Try to unzip the retrieved backup ZIP file
        if (resultCode == RESULT_OK && requestCode == 219) {
            try {
                val uri = data!!.data
                var settingsFileExists = false
                val zis = ZipInputStream(
                    contentResolver.openInputStream(
                        uri!!
                    )
                )
                var zipEntry: ZipEntry
                while (zis.nextEntry.also { zipEntry = it } != null) {
                    if (zipEntry.name == "settings.json") {
                        settingsFileExists = true
                        break
                    }
                }
                zis.closeEntry()
                zis.close()
                if (settingsFileExists) {
                    unZip(this, null, uri)
                    /* todo in the strange case that the same tree suggested by the referrer is imported with the ZIP backup
						   you should cancel the referrer:
						if( decomprimiZip( this, null, uri ) ){
						String idData = Esportatore.estraiNome(uri); // che però non è statico
						if( Global.preferenze.referrer.equals(idData) ) {
							Global.preferenze.referrer = null;
							Global.preferenze.salva();
						}}
					 */
                } else Toast.makeText(
                    this@NewTreeActivity,
                    R.string.backup_invalid,
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(this@NewTreeActivity, e.localizedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }

    fun finishImportingGedcom() {
        onBackPressed()
        Toast.makeText(this, R.string.tree_imported_ok, Toast.LENGTH_SHORT).show()
    }

    /**
     * Back arrow in the toolbar like the hardware one
     */
    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        onBackPressed()
        return true
    }

    companion object {
        /**
         * Unzip a ZIP file in the device storage
         * Used equally by: Simpsons example, backup files and shared trees
         */
        fun unZip(context: Context, zipPath: String?, zipUri: Uri?): Boolean {
            val treeNumber = Global.settings!!.max() + 1
            var mediaDir = context.getExternalFilesDir(treeNumber.toString())
            val sourceDir = context.applicationInfo.sourceDir
            if (!sourceDir.startsWith("/data/")) { // App installed not in internal memory (hopefully moved to SD-card)
                val externalFilesDirs = context.getExternalFilesDirs(treeNumber.toString())
                if (externalFilesDirs.size > 1) {
                    mediaDir = externalFilesDirs[1]
                }
            }
            try {
                val `is`: InputStream?
                `is` =
                    if (zipPath != null) FileInputStream(zipPath) else context.contentResolver.openInputStream(
                        zipUri!!
                    )
                val zis = ZipInputStream(`is`)
                var zipEntry: ZipEntry
                var len: Int
                val buffer = ByteArray(1024)
                var newFile: File
                while (zis.nextEntry.also { zipEntry = it } != null) {
                    newFile = if (zipEntry.name == "tree.json") File(
                        context.filesDir,
                        "$treeNumber.json"
                    ) else if (zipEntry.name == "settings.json") File(
                        context.cacheDir,
                        "settings.json"
                    ) else  // It's a file from the 'media' folder
                        File(mediaDir, zipEntry.name.replace("media/", ""))
                    val fos = FileOutputStream(newFile)
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                    fos.close()
                }
                zis.closeEntry()
                zis.close()
                // Reads the settings and saves them in the preferences
                val settingsFile = File(context.cacheDir, "settings.json")
                var json = FileUtils.readFileToString(settingsFile, "UTF-8")
                json = updateLanguage(json)
                val gson = Gson()
                val zipped = gson.fromJson(json, ZippedTree::class.java)
                val tree = Settings.Tree(
                    treeNumber, zipped.title, mediaDir!!.path,
                    zipped.persons, zipped.generations, zipped.root, zipped.shares.toMutableList(), zipped.grade
                )
                Global.settings!!.add(tree)
                settingsFile.delete()
                // Sharing tree intended for comparison
                if (zipped.grade == 9 && compare(context, tree, false)) {
                    tree.grade = 20 // brands it as derivative
                }
                // The download was done from the referrer dialog in Trees
                if (context is TreesActivity) {
                    val treesPage = context
                    treesPage.runOnUiThread {
                        treesPage.wheel?.visibility = View.GONE
                        treesPage.updateList()
                    }
                } else  // Example tree (Simpson) or backup (from FacadeActivity or NewTree)
                    context.startActivity(Intent(context, TreesActivity::class.java))
                Global.settings!!.save()
                U.toast(context as Activity, R.string.tree_imported_ok)
                return true
            } catch (e: Exception) {
                U.toast(context as Activity, e.localizedMessage)
            }
            return false
        }

        /**
         * Replace Italian with English in the Json settings of ZIP backup
         * Added in Family Gem 0.8
         */
        fun updateLanguage(json: String): String {
            return json.replace("\"generazioni\":", "\"generations\":")
                .replace("\"grado\":", "\"grade\":")
                .replace("\"individui\":", "\"persons\":")
                .replace("\"radice\":", "\"root\":")
                .replace("\"titolo\":", "\"title\":")
                .replace("\"condivisioni\":", "\"shares\":")
                .replace("\"data\":", "\"dateId\":")
        }

        /**
         * Compare the posting dates of existing trees
         * If it finds at least one original tree among the existing ones, it returns true
         * and eventually open the comparator
         */
        @JvmStatic
        fun compare(context: Context, tree2: Settings.Tree, openCompareActivity: Boolean): Boolean {
            if (tree2.shares != null) for (alb in Global.settings!!.trees!!) if (alb.id != tree2.id && alb.shares != null && alb.grade != 20 && alb.grade != 30) for (i in alb.shares!!.indices.reversed()) { // The shares from last to first
                val share = alb.shares!![i]
                for (share2 in tree2.shares!!) if (share.dateId != null && share.dateId == share2.dateId) {
                    if (openCompareActivity) context.startActivity(
                        Intent(context, CompareActivity::class.java)
                            .putExtra(TREE_ID_KEY, alb.id)
                            .putExtra(TREE_2_ID_KEY, tree2.id)
                            .putExtra(DATA_ID_KEY, share.dateId)
                    )
                    return true
                }
            }
            return false
        }

        /**
         * Create the standard header for this app
         */
        @JvmStatic
        fun createHeader(filename: String?): Header {
            val text = Header()
            val app = Generator()
            app.value = "FAMILY_GEM"
            app.name = "Family Gem"
            app.version = BuildConfig.VERSION_NAME
            text.generator = app
            text.file = filename
            val version = GedcomVersion()
            version.form = "LINEAGE-LINKED"
            version.version = "5.5.1"
            text.gedcomVersion = version
            val charSet = CharacterSet()
            charSet.value = "UTF-8"
            text.characterSet = charSet
            val loc = Locale(Locale.getDefault().language)
            // There is also Resources.getSystem().getConfiguration().locale.getLanguage() which returns the same 'it'
            text.language =
                loc.getDisplayLanguage(Locale.ENGLISH) // ok takes system language in english, not local language
            // in the header there are two data fields: TRANSMISSION DATE a bit forcibly can contain the date of the last modification
            text.dateTime = U.actualDateTime()
            return text
        }
    }
}