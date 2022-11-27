package app.familygem

import app.familygem.NewTreeActivity.Companion.createHeader
import app.familygem.ListOfAuthorsFragment.Companion.newAuthor
import app.familygem.BaseActivity
import org.folg.gedcom.model.Gedcom
import app.familygem.Exporter
import android.os.Bundle
import app.familygem.R
import android.widget.EditText
import android.widget.TextView
import org.folg.gedcom.model.Submitter
import app.familygem.U
import android.content.DialogInterface
import app.familygem.NewTreeActivity
import app.familygem.ListOfAuthorsFragment
import android.widget.CheckBox
import app.familygem.SharingActivity.PostDataShareAsyncTask
import android.widget.LinearLayout
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.AsyncTask
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import app.familygem.SharingActivity
import app.familygem.Settings.Share
import app.familygem.SharingActivity.FTPUploadAsyncTask
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTP
import androidx.appcompat.app.AppCompatActivity
import app.familygem.constant.intdefs.PEOPLE_LIST_CHOOSE_RELATIVE_KEY
import app.familygem.constant.intdefs.RELATIVE_ID_KEY
import app.familygem.constant.intdefs.TREE_ID_KEY
import java.io.*
import java.lang.Exception
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SharingActivity : BaseActivity() {
    var gc: Gedcom? = null
    var tree: Settings.Tree? = null
    var exporter: Exporter? = null
    var submitterName: String? = null
    var accessible // 0 = false, 1 = true //TODO why isn't this a boolean?
            = 0
    var dataId: String? = null
    var submitterId: String? = null
    var uploadSuccessful = false
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.condivisione)
        val treeId = intent.getIntExtra(TREE_ID_KEY, 1)
        tree = Global.settings!!.getTree(treeId)

        // Title of the tree
        val titleEditText = findViewById<EditText>(R.id.condividi_titolo)
        titleEditText.setText(tree!!.title)
        if (tree!!.grade == 10) (findViewById<View>(R.id.condividi_tit_autore) as TextView).setText(
            R.string.changes_submitter
        )
        exporter = Exporter(this)
        exporter!!.openTree(treeId)
        gc = Global.gc
        if (gc != null) {
            displayShareRoot()
            // Author name
            val submitters =
                arrayOfNulls<Submitter>(1) //needs to be final one-element array because it is captured by lambda. See https://stackoverflow.com/questions/34865383/variable-used-in-lambda-expression-should-be-final-or-effectively-final
            // tree in Italy with submitter referenced
            if (tree!!.grade == 0 && gc!!.header != null && gc!!.header.getSubmitter(gc) != null) submitters[0] =
                gc!!.header.getSubmitter(gc) else if (tree!!.grade == 0 && !gc!!.submitters.isEmpty()) submitters[0] =
                gc!!.submitters[gc!!.submitters.size - 1] else if (tree!!.grade == 10 && U.newSubmitter(
                    gc!!
                ) != null
            ) submitters[0] = U.newSubmitter(gc!!)
            val authorEditText = findViewById<EditText>(R.id.condividi_autore)
            submitterName = if (submitters[0] == null) "" else submitters[0]!!.name
            authorEditText.setText(submitterName)

            // Display an alert for the acknowledgment of sharing
            if (!Global.settings!!.shareAgreement) {
                AlertDialog.Builder(this).setTitle(R.string.share_sensitive)
                    .setMessage(R.string.aware_upload_server)
                    .setPositiveButton(android.R.string.ok) { dialog: DialogInterface?, id: Int ->
                        Global.settings!!.shareAgreement = true
                        Global.settings!!.save()
                    }.setNeutralButton(R.string.remind_later, null).show()
            }

            // Collect share data and post to database
            findViewById<View>(R.id.bottone_condividi).setOnClickListener { v: View ->
                if (uploadSuccessful) showLinkSharingChooserDialog() else {
                    if (isFilledIn(titleEditText, R.string.please_title) || isFilledIn(
                            authorEditText,
                            R.string.please_name
                        )
                    ) return@setOnClickListener
                    v.isEnabled = false
                    findViewById<View>(R.id.condividi_circolo).visibility = View.VISIBLE

                    // Title of the tree
                    val editedTitle = titleEditText.text.toString()
                    if (tree!!.title != editedTitle) {
                        tree!!.title = editedTitle
                        Global.settings!!.save()
                    }

                    // Submitter update
                    var header = gc!!.header
                    if (header == null) {
                        header = createHeader(tree!!.id.toString() + ".json")
                        gc!!.header = header
                    } else header.dateTime = U.actualDateTime()
                    if (submitters[0] == null) {
                        submitters[0] = newAuthor(null)
                    }
                    if (header.submitterRef == null) {
                        header.submitterRef = submitters[0]!!.id
                    }
                    val editedAuthorName = authorEditText.text.toString()
                    if (editedAuthorName != submitterName) {
                        submitterName = editedAuthorName
                        submitters[0]!!.name = submitterName
                        U.updateChangeDate(submitters[0])
                    }
                    submitterId = submitters[0]!!.id
                    U.saveJson(gc, treeId) // bypassing the preference not to save automatically

                    // Tree accessibility for app developer
                    val accessibleTree = findViewById<CheckBox>(R.id.condividi_allow)
                    accessible = if (accessibleTree.isChecked) 1 else 0

                    // Submit the data
                    if (!BuildConfig.utenteAruba.isEmpty()) PostDataShareAsyncTask().execute(this)
                }
            }
        } else findViewById<View>(R.id.condividi_scatola).visibility = View.GONE
    }

    /**
     * The person root of the tree
     */
    var rootView: View? = null
    fun displayShareRoot() {
        val rootId: String?
        if (tree!!.shareRoot != null && gc!!.getPerson(tree!!.shareRoot) != null) rootId =
            tree!!.shareRoot else if (tree!!.root != null && gc!!.getPerson(
                tree!!.root
            ) != null
        ) {
            rootId = tree!!.root
            tree!!.shareRoot =
                rootId // to be able to share the tree immediately without changing the root
        } else {
            rootId = U.findRoot(gc!!)
            tree!!.shareRoot = rootId
        }
        val person = gc!!.getPerson(rootId)
        if (person != null && tree!!.grade < 10) { // it is only shown on the first share, not on return
            val rootLayout = findViewById<LinearLayout>(R.id.condividi_radice)
            rootLayout.removeView(rootView)
            rootLayout.visibility = View.VISIBLE
            rootView = U.linkPerson(rootLayout, person, 1)
            rootView!!.setOnClickListener(View.OnClickListener { v: View? ->
                val intent = Intent(this, Principal::class.java)
                intent.putExtra(PEOPLE_LIST_CHOOSE_RELATIVE_KEY, true)
                startActivityForResult(intent, 5007)
            })
        }
    }

    /**
     * Check that a field is filled in
     */
    fun isFilledIn(campo: EditText, msg: Int): Boolean {
        val text = campo.text.toString()
        if (text.isEmpty()) {
            campo.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(campo, InputMethodManager.SHOW_IMPLICIT)
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    /**
     * Inserts the summary of the share in the database of www.familygem.app
     * If all goes well create the zip file with the tree and the images
     */
    internal class PostDataShareAsyncTask : AsyncTask<SharingActivity, Void?, SharingActivity>() {
        protected override fun doInBackground(vararg contexts: SharingActivity): SharingActivity {
            val activity = contexts[0]
            try {
                val url = URL("https://www.familygem.app/inserisci.php")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                val out: OutputStream = BufferedOutputStream(conn.outputStream)
                val writer = BufferedWriter(OutputStreamWriter(out, "UTF-8"))
                val data = "password=" + URLEncoder.encode(BuildConfig.passwordAruba, "UTF-8") +
                        "&titoloAlbero=" + URLEncoder.encode(activity.tree!!.title, "UTF-8") +
                        "&nomeAutore=" + URLEncoder.encode(activity.submitterName, "UTF-8") +
                        "&accessibile=" + activity.accessible
                writer.write(data)
                writer.flush()
                writer.close()
                out.close()

                // Response
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val line1 = reader.readLine()
                reader.close()
                conn.disconnect()
                if (line1.startsWith("20")) {
                    activity.dataId = line1.replace("[-: ]".toRegex(), "")
                    val share = Share(activity.dataId!!, activity.submitterId!!)
                    activity.tree!!.aggiungiCondivisione(share)
                    Global.settings!!.save()
                }
            } catch (e: Exception) {
                U.toast(activity, e.localizedMessage)
            }
            return activity
        }

        override fun onPostExecute(activity: SharingActivity) {
            if (activity.dataId != null && activity.dataId!!.startsWith("20")) {
                val fileTree = File(activity.cacheDir, activity.dataId + ".zip")
                if (activity.exporter!!.exportBackupZip(
                        activity.tree!!.shareRoot,
                        9,
                        Uri.fromFile(fileTree)
                    )
                ) {
                    FTPUploadAsyncTask().execute(activity)
                    return
                } else Toast.makeText(activity, activity.exporter!!.errorMessage, Toast.LENGTH_LONG)
                    .show()
            }
            // An error Toast here would replace the toast() message in catch()
            activity.findViewById<View>(R.id.bottone_condividi).isEnabled = true
            activity.findViewById<View>(R.id.condividi_circolo).visibility =
                View.INVISIBLE
        }
    }

    /**
     * Upload the zip file with the shared tree by ftp.
     */
    internal class FTPUploadAsyncTask : AsyncTask<SharingActivity, Void?, SharingActivity>() {
        protected override fun doInBackground(vararg contesti: SharingActivity): SharingActivity {
            val activity = contesti[0]
            try {
                val ftpClient = FTPClient()
                ftpClient.connect("89.46.104.211", 21)
                ftpClient.enterLocalPassiveMode()
                ftpClient.login(BuildConfig.utenteAruba, BuildConfig.passwordAruba)
                ftpClient.changeWorkingDirectory("/www.familygem.app/condivisi")
                ftpClient.setFileType(FTP.BINARY_FILE_TYPE)
                val buffIn: BufferedInputStream
                val zipName = activity.dataId + ".zip"
                buffIn =
                    BufferedInputStream(FileInputStream(activity.cacheDir.toString() + "/" + zipName))
                activity.uploadSuccessful = ftpClient.storeFile(zipName, buffIn)
                buffIn.close()
                ftpClient.logout()
                ftpClient.disconnect()
            } catch (e: Exception) {
                U.toast(activity, e.localizedMessage)
            }
            return activity
        }

        override fun onPostExecute(activity: SharingActivity) {
            if (activity.uploadSuccessful) {
                Toast.makeText(activity, R.string.correctly_uploaded, Toast.LENGTH_SHORT).show()
                activity.showLinkSharingChooserDialog()
            } else {
                activity.findViewById<View>(R.id.bottone_condividi).isEnabled = true
                activity.findViewById<View>(R.id.condividi_circolo).visibility =
                    View.INVISIBLE
            }
        }
    }

    /**
     * Show apps to share the link
     */
    fun showLinkSharingChooserDialog() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/plain"
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.sharing_tree))
        intent.putExtra(
            Intent.EXTRA_TEXT, getString(
                R.string.click_this_link,
                "https://www.familygem.app/share.php?tree=$dataId"
            )
        )
        //startActivity( Intent.createChooser( intent, "Condividi con" ) );
        /*
			Coming back from a messaging app the requestCode 35417 always arrives correct
			Instead the resultCode can be RESULT_OK or RESULT_CANCELED at head
			For example from Gmail it always comes back with RESULT_CANCELED whether the email has been sent or not
			also when sending an SMS it returns RESULT_CANCELED even if the SMS has been sent
			or from Whatsapp it is RESULT_OK whether the message was sent or not
			In practice, there is no way to know if the message has been sent in the messaging app
		*/startActivityForResult(Intent.createChooser(intent, getText(R.string.share_with)), 35417)
        findViewById<View>(R.id.bottone_condividi).isEnabled = true
        findViewById<View>(R.id.condividi_circolo).visibility = View.INVISIBLE
    }

    /**
     * Update the preferences so as to show the new root chosen in the ListOfPeopleActivity.
     * See links in comment in [.showLinkSharingChooserDialog] for meaning of request codes
     */
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            if (requestCode == 5007) {
                tree!!.shareRoot = data!!.getStringExtra(RELATIVE_ID_KEY)
                Global.settings!!.save()
                displayShareRoot()
            }
        }
        // Return from any sharing app, whether the message was sent or not
        if (requestCode == 35417) {
            // Todo close keyboard
            Toast.makeText(applicationContext, R.string.sharing_completed, Toast.LENGTH_LONG).show()
        }
    }

    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        onBackPressed()
        return true
    }
}