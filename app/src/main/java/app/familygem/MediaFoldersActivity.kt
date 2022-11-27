package app.familygem

import android.Manifest
import app.familygem.F.uriPathFolderKitKat
import app.familygem.F.uriFolderPath
import app.familygem.BaseActivity
import android.os.Bundle
import app.familygem.R
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import app.familygem.SpeechBubble
import android.os.Build
import android.content.Intent
import android.widget.LinearLayout
import android.widget.TextView
import android.content.DialogInterface
import androidx.documentfile.provider.DocumentFile
import android.app.Activity
import android.net.Uri
import app.familygem.F
import android.widget.Toast
import android.view.ContextMenu
import android.view.ContextMenu.ContextMenuInfo
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import app.familygem.U
import app.familygem.constant.intdefs.TREE_ID_KEY
import java.util.ArrayList

/**
 * Activity where you can see the list of folders, add, delete
 */
class MediaFoldersActivity : BaseActivity() {
    var treeId = 0
    var folders: MutableList<String>? = null
    var uris: MutableList<String>? = null
    override fun onCreate(bandolo: Bundle?) {
        super.onCreate(bandolo)
        setContentView(R.layout.cartelle_media)
        treeId = intent.getIntExtra(TREE_ID_KEY, 0)
        folders = ArrayList(Global.settings!!.getTree(treeId)!!.dirs)
        uris = ArrayList(Global.settings!!.getTree(treeId)!!.uris)
        updateList()
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
            val perm =
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (perm == PackageManager.PERMISSION_DENIED) ActivityCompat.requestPermissions(
                this, arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ), 3517
            ) else if (perm == PackageManager.PERMISSION_GRANTED) doChooseFolder()
        }
        if (Global.settings!!.getTree(treeId)!!.dirs.isEmpty() && Global.settings!!.getTree(treeId)!!.uris!!.isEmpty()) SpeechBubble(
            this,
            R.string.add_device_folder
        ).show()
    }

    fun doChooseFolder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, 123)
        } else {
            // KitKat uses the selection of a file to find the folder
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "*/*"
            startActivityForResult(intent, 456)
        }
    }

    fun updateList() {
        val layout = findViewById<LinearLayout>(R.id.cartelle_scatola)
        layout.removeAllViews()
        for (cart in folders!!) {
            val folderView = layoutInflater.inflate(R.layout.pezzo_cartella, layout, false)
            layout.addView(folderView)
            val nameView = folderView.findViewById<TextView>(R.id.cartella_nome)
            val urlView = folderView.findViewById<TextView>(R.id.cartella_url)
            urlView.text = cart
            if (Global.settings!!.expert) urlView.isSingleLine = false
            val deleteButton = folderView.findViewById<View>(R.id.cartella_elimina)
            // The '/storage/.../Android/data/app.familygem/files/X' folder should be preserved as it is the default copied media
            // Also in Android 11 it is no longer reachable by the user with SAF
            if (cart == getExternalFilesDir(null).toString() + "/" + treeId) {
                nameView.setText(R.string.app_storage)
                deleteButton.visibility = View.GONE
            } else {
                nameView.text = folderName(cart)
                deleteButton.setOnClickListener { v: View? ->
                    AlertDialog.Builder(this).setMessage(R.string.sure_delete)
                        .setPositiveButton(R.string.yes) { di: DialogInterface?, id: Int ->
                            folders!!.remove(cart)
                            save()
                        }.setNeutralButton(R.string.cancel, null).show()
                }
            }
            registerForContextMenu(folderView)
        }
        for (stringUri in uris!!) {
            val uriView = layoutInflater.inflate(R.layout.pezzo_cartella, layout, false)
            layout.addView(uriView)
            val documentDir = DocumentFile.fromTreeUri(this, Uri.parse(stringUri))
            var name: String? = null
            if (documentDir != null) name = documentDir.name
            (uriView.findViewById<View>(R.id.cartella_nome) as TextView).text = name
            val urlView = uriView.findViewById<TextView>(R.id.cartella_url)
            if (Global.settings!!.expert) {
                urlView.isSingleLine = false
                urlView.text = stringUri
            } else urlView.text =
                Uri.decode(stringUri) // lo mostra decodificato cioè un po' più leggibile
            uriView.findViewById<View>(R.id.cartella_elimina).setOnClickListener { v: View? ->
                AlertDialog.Builder(this).setMessage(R.string.sure_delete)
                    .setPositiveButton(R.string.yes) { di: DialogInterface?, id: Int ->
                        // Revoke permission for this uri, if the uri is not used in any other tree
                        var uriExistsElsewhere = false
                        for (tree in Global.settings!!.trees!!) {
                            for (uri in tree.uris!!) if (uri == stringUri && tree.id != treeId) {
                                uriExistsElsewhere = true
                                break
                            }
                        }
                        if (!uriExistsElsewhere) revokeUriPermission(
                            Uri.parse(stringUri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        uris!!.remove(stringUri)
                        save()
                    }.setNeutralButton(R.string.cancel, null).show()
            }
            registerForContextMenu(uriView)
        }
    }

    fun folderName(url: String): String {
        return if (url.lastIndexOf('/') > 0) url.substring(url.lastIndexOf('/') + 1) else url
    }

    fun save() {
        Global.settings!!.getTree(treeId)!!.dirs.clear()
        for (path in folders!!) Global.settings!!.getTree(treeId)!!.dirs.add(
            path
        )
        Global.settings!!.getTree(treeId)!!.uris?.clear()
        for (uri in uris!!) Global.settings!!.getTree(treeId)!!.uris?.add(uri)
        Global.settings!!.save()
        updateList()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        onBackPressed()
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val uri = data!!.data
            if (uri != null) {
                // in KitKat a file has been selected and we get the path to the folder
                if (requestCode == 456) {
                    val path = uriPathFolderKitKat(this, uri)
                    if (path != null) {
                        folders!!.add(path)
                        save()
                    }
                } else if (requestCode == 123) {
                    val path = uriFolderPath(uri)
                    if (path != null) {
                        folders!!.add(path)
                        save()
                    } else {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        val docDir = DocumentFile.fromTreeUri(this, uri)
                        if (docDir != null && docDir.canRead()) {
                            uris!!.add(uri.toString())
                            save()
                        } else Toast.makeText(
                            this,
                            "Could not read this position.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else Toast.makeText(this, R.string.something_wrong, Toast.LENGTH_SHORT).show()
        }
    }

    var menu: View? = null
    override fun onCreateContextMenu(menu: ContextMenu, vista: View, info: ContextMenuInfo) {
        this.menu = vista
        menu.add(0, 0, 0, R.string.copy)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 0) { // Copy
            U.copyToClipboard(
                getText(android.R.string.copyUrl),
                (menu!!.findViewById<View>(R.id.cartella_url) as TextView).text
            )
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.size > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && requestCode == 3517) doChooseFolder()
    }
}