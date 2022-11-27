package app.familygem

import android.content.Context
import app.familygem.FacadeActivity.Companion.downloadShared
import app.familygem.NewTreeActivity.Companion.compare
import app.familygem.F.saveDocument
import app.familygem.TreeInfoActivity.Companion.refreshData
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.DialogInterface
import android.view.ViewGroup
import android.content.Intent
import android.os.Handler
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import app.familygem.constant.intdefs.*
import app.familygem.visitor.MediaList
import com.android.installreferrer.api.InstallReferrerClient
import com.android.installreferrer.api.InstallReferrerStateListener
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.parser.JsonParser
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.lang.Error
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*

class TreesActivity : AppCompatActivity() {
    var treeList: MutableList<Map<String, String>>? = null
    var adapter: SimpleAdapter? = null
    var wheel //TODO rename to progress
            : View? = null
    var welcome: SpeechBubble? = null
    var exporter: Exporter? = null

    /**
     * To open automatically the tree at startup only once
     */
    private var autoOpenedTree = false

    /**
     * The birthday notification IDs are stored to display the corresponding person only once
     */
    private var consumedNotifications: ArrayList<Int>? = ArrayList()
    override fun onCreate(savedState: Bundle?) {
        super.onCreate(savedState)
        setContentView(R.layout.trees)
        val listView = findViewById<ListView>(R.id.trees_list)
        wheel = findViewById(R.id.trees_progress)
        welcome = SpeechBubble(this, R.string.tap_add_tree)
        exporter = Exporter(this)

        // At the very first start
        val referrer = Global.settings!!.referrer
        if (referrer == "start") fetchReferrer() else if (referrer?.matches(
                "[0-9]{14}".toRegex()
            ) == true
        ) {
            AlertDialog.Builder(this).setTitle(R.string.a_new_tree)
                .setMessage(R.string.you_can_download)
                .setPositiveButton(R.string.download) { dialog: DialogInterface?, id: Int ->
                    downloadShared(
                        this,
                        referrer,
                        wheel
                    )
                }
                .setNeutralButton(R.string.cancel, null).show()
        } // If there is no tree
        else if (Global.settings!!.trees!!.isEmpty()) welcome!!.show()
        if (savedState != null) {
            autoOpenedTree = savedState.getBoolean(AUTO_OPENED_TREE_KEY)
            consumedNotifications = savedState.getIntegerArrayList(CONSUMED_NOTIFICATIONS_KEY)
        }
        if (Global.settings!!.trees != null) {

            // List of family trees
            treeList = ArrayList()

            // Feed the data to the adapter
            adapter = object : SimpleAdapter(
                this,
                treeList,
                R.layout.pezzo_albero,
                arrayOf("titolo", "dati"),
                intArrayOf(R.id.albero_titolo, R.id.albero_dati)
            ) {
                //TODO just implement custom adapter? This is part of the outdated android.widget package, and has a slightly clunky API compared to a custom adapter.
                // Locate each view in the list
                override fun getView(position: Int, convertView: View, parent: ViewGroup): View {
                    val treeView = super.getView(position, convertView, parent)
                    val treeId = (treeList as ArrayList<Map<String, String>>)[position]["id"]!!.toInt()
                    val tree = Global.settings!!.getTree(treeId)
                    val derivative = tree!!.grade == 20
                    val noNovelties = tree.grade == 30
                    if (derivative) {
                        treeView.setBackgroundColor(resources.getColor(R.color.accent_medium))
                        (treeView.findViewById<View>(R.id.albero_dati) as TextView).setTextColor(
                            resources.getColor(R.color.text)
                        )
                        treeView.setOnClickListener { v: View? ->
                            if (!compare(this@TreesActivity, tree, true)) {
                                tree.grade = 10 // is demoted
                                Global.settings!!.save()
                                updateList()
                                Toast.makeText(
                                    this@TreesActivity,
                                    R.string.something_wrong,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else if (noNovelties) {
                        treeView.setBackgroundColor(resources.getColor(R.color.consumed))
                        (treeView.findViewById<View>(R.id.albero_titolo) as TextView).setTextColor(
                            resources.getColor(R.color.gray_text)
                        )
                        treeView.setOnClickListener { v: View? ->
                            if (!compare(this@TreesActivity, tree, true)) {
                                tree.grade = 10 // is demoted
                                Global.settings!!.save()
                                updateList()
                                Toast.makeText(
                                    this@TreesActivity,
                                    R.string.something_wrong,
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        treeView.setBackgroundColor(resources.getColor(R.color.back_element))
                        treeView.setOnClickListener { v: View? ->
                            wheel?.visibility = View.VISIBLE
                            if (!(Global.gc != null && treeId == Global.settings!!.openTree)) { // if it's not already open
                                if (!openGedcom(treeId, true)) {
                                    wheel?.visibility = View.GONE
                                    return@setOnClickListener
                                }
                            }
                            startActivity(Intent(this@TreesActivity, Principal::class.java))
                        }
                    }
                    treeView.findViewById<View>(R.id.albero_menu)
                        .setOnClickListener { vista: View? ->
                            val exists = File(filesDir, "$treeId.json").exists()
                            val popup = PopupMenu(this@TreesActivity, vista!!)
                            val menu = popup.menu
                            if (treeId == Global.settings!!.openTree && Global.shouldSave) menu.add(
                                0,
                                -1,
                                0,
                                R.string.save
                            )
                            if (Global.settings!!.expert && derivative || Global.settings!!.expert && noNovelties) menu.add(
                                0,
                                0,
                                0,
                                R.string.open
                            )
                            if (!noNovelties || Global.settings!!.expert) menu.add(
                                0,
                                1,
                                0,
                                R.string.tree_info
                            )
                            if (!derivative && !noNovelties || Global.settings!!.expert) menu.add(
                                0,
                                2,
                                0,
                                R.string.rename
                            )
                            if (exists && (!derivative || Global.settings!!.expert) && !noNovelties) menu.add(
                                0,
                                3,
                                0,
                                R.string.media_folders
                            )
                            if (!noNovelties) menu.add(0, 4, 0, R.string.find_errors)
                            if (exists && !derivative && !noNovelties) // non si può ri-condividere un albero ricevuto indietro, anche se sei esperto..
                                menu.add(0, 5, 0, R.string.share_tree)
                            if (exists && !derivative && !noNovelties && Global.settings!!.expert && Global.settings!!.trees!!.size > 1 && tree.shares != null && tree.grade != 0) // cioè dev'essere 9 o 10
                                menu.add(0, 6, 0, R.string.compare)
                            if (exists && Global.settings!!.expert && !noNovelties) menu.add(
                                0,
                                7,
                                0,
                                R.string.export_gedcom
                            )
                            if (exists && Global.settings!!.expert) menu.add(
                                0,
                                8,
                                0,
                                R.string.make_backup
                            )
                            menu.add(0, 9, 0, R.string.delete)
                            popup.show()
                            popup.setOnMenuItemClickListener { item: MenuItem ->
                                val id = item.itemId
                                if (id == -1) { // Save
                                    U.saveJson(Global.gc, treeId)
                                    Global.shouldSave = false
                                } else if (id == 0) { // Opens a child tree
                                    openGedcom(treeId, true)
                                    startActivity(Intent(this@TreesActivity, Principal::class.java))
                                } else if (id == 1) { // Info Gedcom
                                    val intent =
                                        Intent(this@TreesActivity, TreeInfoActivity::class.java)
                                    intent.putExtra(TREE_ID_KEY, treeId)
                                    startActivity(intent)
                                } else if (id == 2) { // Rename tree
                                    val builder = AlertDialog.Builder(this@TreesActivity)
                                    val messageView = layoutInflater.inflate(
                                        R.layout.albero_nomina,
                                        listView,
                                        false
                                    )
                                    builder.setView(messageView).setTitle(R.string.title)
                                    val nameEditText =
                                        messageView.findViewById<EditText>(R.id.nuovo_nome_albero)
                                    nameEditText.setText((treeList as ArrayList<Map<String, String>>).get(position)["titolo"])
                                    val dialog =
                                        builder.setPositiveButton(R.string.rename) { view: DialogInterface?, i1: Int ->
                                            Global.settings!!.rename(
                                                treeId,
                                                nameEditText.text.toString()
                                            )
                                            updateList()
                                        }.setNeutralButton(R.string.cancel, null).create()
                                    nameEditText.setOnEditorActionListener { view: TextView?, action: Int, event: KeyEvent? ->
                                        if (action == EditorInfo.IME_ACTION_DONE) dialog.getButton(
                                            AlertDialog.BUTTON_POSITIVE
                                        ).performClick()
                                        false
                                    }
                                    dialog.show()
                                    messageView.postDelayed({
                                        nameEditText.requestFocus()
                                        nameEditText.setSelection(nameEditText.text.length)
                                        val inputMethodManager =
                                            getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                                        inputMethodManager.showSoftInput(
                                            nameEditText,
                                            InputMethodManager.SHOW_IMPLICIT
                                        )
                                    }, 300)
                                } else if (id == 3) { // Media folders
                                    startActivity(
                                        Intent(this@TreesActivity, MediaFoldersActivity::class.java)
                                            .putExtra(TREE_ID_KEY, treeId)
                                    )
                                } else if (id == 4) { // Correct errors
                                    findErrors(treeId, false)
                                } else if (id == 5) { // Share tree
                                    startActivity(
                                        Intent(this@TreesActivity, SharingActivity::class.java)
                                            .putExtra(TREE_ID_KEY, treeId)
                                    )
                                } else if (id == 6) { // Compare with existing trees
                                    if (compare(this@TreesActivity, tree, false)) {
                                        tree.grade = 20
                                        updateList()
                                    } else Toast.makeText(
                                        this@TreesActivity,
                                        R.string.no_results,
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else if (id == 7) { // Export Gedcom
                                    if (exporter!!.openTree(treeId)) {
                                        var mime = "application/octet-stream"
                                        var ext = "ged"
                                        var code = 636
                                        if (exporter!!.numMediaFilesToAttach() > 0) {
                                            mime = "application/zip"
                                            ext = "zip"
                                            code = 6219
                                        }
                                        saveDocument(
                                            this@TreesActivity,
                                            null,
                                            treeId,
                                            mime,
                                            ext,
                                            code
                                        )
                                    }
                                } else if (id == 8) { // Make backups
                                    if (exporter!!.openTree(treeId)) saveDocument(
                                        this@TreesActivity,
                                        null,
                                        treeId,
                                        "application/zip",
                                        "zip",
                                        327
                                    )
                                } else if (id == 9) {    // Delete tree
                                    AlertDialog.Builder(this@TreesActivity)
                                        .setMessage(R.string.really_delete_tree)
                                        .setPositiveButton(R.string.delete) { dialog: DialogInterface?, id1: Int ->
                                            deleteTree(this@TreesActivity, treeId)
                                            updateList()
                                        }.setNeutralButton(R.string.cancel, null).show()
                                } else {
                                    return@setOnMenuItemClickListener false
                                }
                                true
                            }
                        }
                    return treeView
                }
            }
            listView.adapter = adapter
            updateList()
        }

        // Custom bar
        val toolbar = supportActionBar
        val treeToolbar = layoutInflater.inflate(R.layout.trees_bar, null)
        treeToolbar.findViewById<View>(R.id.trees_settings).setOnClickListener { v: View? ->
            startActivity(
                Intent(this, OptionsActivity::class.java)
            )
        }
        toolbar!!.customView = treeToolbar
        toolbar.setDisplayShowCustomEnabled(true)

        // FAB
        findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
            welcome!!.hide()
            startActivity(Intent(this, NewTreeActivity::class.java))
        }

        // Automatic load of last opened tree of previous session
        if (!birthdayNotifyTapped(intent) && !autoOpenedTree
            && intent.getBooleanExtra(
                OPEN_TREE_AUTOMATICALLY_KEY,
                false
            ) && Global.settings!!.openTree > 0
        ) {
            listView.post {
                if (openGedcom(Global.settings!!.openTree, false)) {
                    wheel?.setVisibility(View.VISIBLE)
                    autoOpenedTree = true
                    startActivity(Intent(this, Principal::class.java))
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Hides the wheel, especially when navigating back to this activity
        wheel!!.visibility = View.GONE
    }

    /**
     * Trees being launchMode=singleTask, onRestart is also called with startActivity (except the first one)
     * but obviously only if [TreesActivity] has called onStop (doing it fast calls only onPause)
     */
    override fun onRestart() {
        super.onRestart()
        updateList()
    }

    /**
     * New intent coming from a tapped notification
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        birthdayNotifyTapped(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(AUTO_OPENED_TREE_KEY, autoOpenedTree)
        outState.putIntegerArrayList(CONSUMED_NOTIFICATIONS_KEY, consumedNotifications)
        super.onSaveInstanceState(outState)
    }

    /**
     * If a birthday notification was tapped loads the relative tree and returns true
     */
    private fun birthdayNotifyTapped(intent: Intent): Boolean {
        val treeId = intent.getIntExtra(Notifier.TREE_ID_KEY, 0)
        val notifyId = intent.getIntExtra(Notifier.NOTIFY_ID_KEY, 0)
        if (treeId > 0 && !consumedNotifications!!.contains(notifyId)) {
            Handler().post {
                if (openGedcom(treeId, true)) {
                    wheel!!.visibility = View.VISIBLE
                    Global.indi = intent.getStringExtra(Notifier.INDI_ID_KEY)
                    consumedNotifications!!.add(notifyId)
                    startActivity(Intent(this, Principal::class.java))
                    Notifier(
                        this,
                        Global.gc!!,
                        treeId,
                        Notifier.What.DEFAULT
                    ) // Actually delete present notification
                }
            }
            return true
        }
        return false
    }

    /**
     * Try to retrieve the dataID from the Play Store in case the app was installed following a share
     * If it finds the dataid it offers to download the shared tree
     */
    fun fetchReferrer() {
        val irc = InstallReferrerClient.newBuilder(this).build()
        irc.startConnection(object : InstallReferrerStateListener {
            override fun onInstallReferrerSetupFinished(reply: Int) {
                when (reply) {
                    InstallReferrerClient.InstallReferrerResponse.OK -> try {
                        val details = irc.installReferrer
                        // Normally 'referrer' is a string type 'utm_source=google-play&utm_medium=organic'
                        // But if the app was installed from the link in the share page it will be a data-id like '20191003215337'
                        val referrer = details.installReferrer
                        if (referrer?.matches("[0-9]{14}".toRegex()) == true) { // It's a dateId
                            Global.settings!!.referrer = referrer
                            AlertDialog.Builder(this@TreesActivity).setTitle(R.string.a_new_tree)
                                .setMessage(R.string.you_can_download)
                                .setPositiveButton(R.string.download) { dialog: DialogInterface?, id: Int ->
                                    downloadShared(
                                        this@TreesActivity,
                                        referrer,
                                        wheel
                                    )
                                }
                                .setNeutralButton(R.string.cancel) { di: DialogInterface?, id: Int -> welcome!!.show() }
                                .setOnCancelListener { d: DialogInterface? -> welcome!!.show() }
                                .show()
                        } else { // It's anything else
                            Global.settings!!.referrer =
                                null // we cancel it so we won't look for it again
                            welcome!!.show()
                        }
                        Global.settings!!.save()
                        irc.endConnection()
                    } catch (e: Exception) {
                        U.toast(this@TreesActivity, e.localizedMessage)
                    }
                    InstallReferrerClient.InstallReferrerResponse.FEATURE_NOT_SUPPORTED, InstallReferrerClient.InstallReferrerResponse.SERVICE_UNAVAILABLE -> {
                        Global.settings!!.referrer = null // so we never come back here
                        Global.settings!!.save()
                        welcome!!.show()
                    }
                }
            }

            override fun onInstallReferrerServiceDisconnected() {
                // Never seen it appear
                U.toast(this@TreesActivity, "Install Referrer Service Disconnected")
            }
        })
    }

    fun updateList() {
        treeList!!.clear()
        for (alb in Global.settings!!.trees!!) {
            val data: MutableMap<String, String> = HashMap(3)
            data["id"] = alb.id.toString()
            data["titolo"] = alb.title
            // If Gedcom is already open, update the data
            if (Global.gc != null && Global.settings!!.openTree == alb.id && alb.persons < 100) refreshData(
                Global.gc!!, alb
            )
            data["dati"] = writeData(this, alb)
            treeList!!.add(data)
        }
        adapter!!.notifyDataSetChanged()
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            val uri = data!!.data
            var result = false
            if (requestCode == 636) { // Export the GEDCOM
                result = exporter!!.exportGedcom(uri!!)
            } else if (requestCode == 6219) { // Export the zipped GEDCOM with media
                result = exporter!!.exportGedcomToZip(uri)
            } // Export the ZIP backup
            else if (requestCode == 327) {
                result = exporter!!.exportBackupZip(null, -1, uri)
            }
            if (result) Toast.makeText(
                this@TreesActivity,
                exporter!!.successMessage,
                Toast.LENGTH_SHORT
            ).show() else Toast.makeText(
                this@TreesActivity,
                exporter!!.errorMessage,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun findErrors(treeId: Int, correct: Boolean): Gedcom? {
        val gc = readJson(treeId)
            ?: // do you do something to recover an untraceable file..?
            return null
        var errors = 0
        var num: Int
        // Root in preferences
        val tree = Global.settings!!.getTree(treeId)
        val root = gc.getPerson(tree!!.root)
        // Root points to a non-existent person
        if (tree.root != null && root == null) {
            if (!gc.people.isEmpty()) {
                if (correct) {
                    tree.root = U.findRoot(gc)
                    Global.settings!!.save()
                } else errors++
            } else { // tree without people
                if (correct) {
                    tree.root = null
                    Global.settings!!.save()
                } else errors++
            }
        }
        // Or a root is not indicated in preferences even though there are people in the tree
        if (root == null && !gc.people.isEmpty()) {
            if (correct) {
                tree.root = U.findRoot(gc)
                Global.settings!!.save()
            } else errors++
        }
        // Or a shareRoot is listed in preferences that doesn't exist
        val shareRoot = gc.getPerson(tree.shareRoot)
        if (tree.shareRoot != null && shareRoot == null) {
            if (correct) {
                tree.shareRoot = null // just delete it
                Global.settings!!.save()
            } else errors++
        }
        // Search for empty or single-member families to eliminate them
        for (f in gc.families) {
            if (f.husbandRefs.size + f.wifeRefs.size + f.childRefs.size <= 1) {
                if (correct) {
                    gc.families.remove(f) // in doing so you leave the refs in the orphaned individuals of the family to which they refer...
                    // but there's the rest of the checker to fix them
                    break
                } else errors++
            }
        }
        // Silently delete empty list of families
        if (gc.families.isEmpty() && correct) {
            gc.families = null
        }
        // References from a person to the parents' and children's family
        for (p in gc.people) {
            for (pfr in p.parentFamilyRefs) {
                val fam = gc.getFamily(pfr.ref)
                if (fam == null) {
                    if (correct) {
                        p.parentFamilyRefs.remove(pfr)
                        break
                    } else errors++
                } else {
                    num = 0
                    for (cr in fam.childRefs) if (cr.ref == null) {
                        if (correct) {
                            fam.childRefs.remove(cr)
                            break
                        } else errors++
                    } else if (cr.ref == p.id) {
                        num++
                        if (num > 1 && correct) {
                            fam.childRefs.remove(cr)
                            break
                        }
                    }
                    if (num != 1) {
                        if (correct && num == 0) {
                            p.parentFamilyRefs.remove(pfr)
                            break
                        } else errors++
                    }
                }
            }
            // Remove empty list of parent family refs
            if (p.parentFamilyRefs.isEmpty() && correct) {
                p.parentFamilyRefs = null
            }
            for (sfr in p.spouseFamilyRefs) {
                val fam = gc.getFamily(sfr.ref)
                if (fam == null) {
                    if (correct) {
                        p.spouseFamilyRefs.remove(sfr)
                        break
                    } else errors++
                } else {
                    num = 0
                    for (sr in fam.husbandRefs) if (sr.ref == null) {
                        if (correct) {
                            fam.husbandRefs.remove(sr)
                            break
                        } else errors++
                    } else if (sr.ref == p.id) {
                        num++
                        if (num > 1 && correct) {
                            fam.husbandRefs.remove(sr)
                            break
                        }
                    }
                    for (sr in fam.wifeRefs) {
                        if (sr.ref == null) {
                            if (correct) {
                                fam.wifeRefs.remove(sr)
                                break
                            } else errors++
                        } else if (sr.ref == p.id) {
                            num++
                            if (num > 1 && correct) {
                                fam.wifeRefs.remove(sr)
                                break
                            }
                        }
                    }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            p.spouseFamilyRefs.remove(sfr)
                            break
                        } else errors++
                    }
                }
            }
            // Remove empty list of spouse family refs
            if (p.spouseFamilyRefs.isEmpty() && correct) {
                p.spouseFamilyRefs = null
            }
            // References to non-existent Media
            // ok but ONLY for people, maybe it should be done with the Visitor for everyone else
            num = 0
            for (mr in p.mediaRefs) {
                val med = gc.getMedia(mr.ref)
                if (med == null) {
                    if (correct) {
                        p.mediaRefs.remove(mr)
                        break
                    } else errors++
                } else {
                    if (mr.ref == med.id) {
                        num++
                        if (num > 1) if (correct) {
                            p.mediaRefs.remove(mr)
                            break
                        } else errors++
                    }
                }
            }
        }
        // References from each family to the persons belonging to it
        for (f in gc.families) {
            // Husbands refs
            for (sr in f.husbandRefs) {
                val husband = gc.getPerson(sr.ref)
                if (husband == null) {
                    if (correct) {
                        f.husbandRefs.remove(sr)
                        break
                    } else errors++
                } else {
                    num = 0
                    for (sfr in husband.spouseFamilyRefs) if (sfr.ref == null) {
                        if (correct) {
                            husband.spouseFamilyRefs.remove(sfr)
                            break
                        } else errors++
                    } else if (sfr.ref == f.id) {
                        num++
                        if (num > 1 && correct) {
                            husband.spouseFamilyRefs.remove(sfr)
                            break
                        }
                    }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            f.husbandRefs.remove(sr)
                            break
                        } else errors++
                    }
                }
            }
            // Remove empty list of husband refs
            if (f.husbandRefs.isEmpty() && correct) {
                f.husbandRefs = null
            }
            // Wives refs
            for (sr in f.wifeRefs) {
                val wife = gc.getPerson(sr.ref)
                if (wife == null) {
                    if (correct) {
                        f.wifeRefs.remove(sr)
                        break
                    } else errors++
                } else {
                    num = 0
                    for (sfr in wife.spouseFamilyRefs) if (sfr.ref == null) {
                        if (correct) {
                            wife.spouseFamilyRefs.remove(sfr)
                            break
                        } else errors++
                    } else if (sfr.ref == f.id) {
                        num++
                        if (num > 1 && correct) {
                            wife.spouseFamilyRefs.remove(sfr)
                            break
                        }
                    }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            f.wifeRefs.remove(sr)
                            break
                        } else errors++
                    }
                }
            }
            // Remove empty list of wife refs
            if (f.wifeRefs.isEmpty() && correct) {
                f.wifeRefs = null
            }
            // Children refs
            for (cr in f.childRefs) {
                val child = gc.getPerson(cr.ref)
                if (child == null) {
                    if (correct) {
                        f.childRefs.remove(cr)
                        break
                    } else errors++
                } else {
                    num = 0
                    for (pfr in child.parentFamilyRefs) if (pfr.ref == null) {
                        if (correct) {
                            child.parentFamilyRefs.remove(pfr)
                            break
                        } else errors++
                    } else if (pfr.ref == f.id) {
                        num++
                        if (num > 1 && correct) {
                            child.parentFamilyRefs.remove(pfr)
                            break
                        }
                    }
                    if (num != 1) {
                        if (num == 0 && correct) {
                            f.childRefs.remove(cr)
                            break
                        } else errors++
                    }
                }
            }
            // Remove empty list of child refs
            if (f.childRefs.isEmpty() && correct) {
                f.childRefs = null
            }
        }

        // Adds a 'TYPE' tag to name types that don't have it
        for (person in gc.people) {
            for (name in person.names) {
                if (name.type != null && name.typeTag == null) {
                    if (correct) name.typeTag = "TYPE" else errors++
                }
            }
        }

        // Adds a 'FILE' tag to Media that don't have it
        val mediaList = MediaList(gc, ALL_MEDIA)
        gc.accept(mediaList)
        for (med in mediaList.list) {
            if (med.fileTag == null) {
                if (correct) med.fileTag = "FILE" else errors++
            }
        }
        if (!correct) {
            val dialog = AlertDialog.Builder(this)
            dialog.setMessage(
                if (errors == 0) getText(R.string.all_ok) else getString(
                    R.string.errors_found,
                    errors
                )
            )
            if (errors > 0) {
                dialog.setPositiveButton(R.string.correct) { dialogo: DialogInterface, i: Int ->
                    dialogo.cancel()
                    val gcCorretto = findErrors(treeId, true)
                    U.saveJson(gcCorretto, treeId)
                    Global.gc = null // so if it was open then reload it correct
                    findErrors(treeId, false) // reopen to admire (??) the result
                    updateList()
                }
            }
            dialog.setNeutralButton(android.R.string.cancel, null).show()
        }
        return gc
    }

    companion object {
        fun writeData(context: Context, alb: Settings.Tree): String {
            var dati = alb.persons.toString() + " " +
                    context.getString(if (alb.persons == 1) R.string.person else R.string.persons)
                        .lowercase(
                            Locale.getDefault()
                        )
            if (alb.persons > 1 && alb.generations > 0) dati += " - " + alb.generations + " " +
                    context.getString(if (alb.generations == 1) R.string.generation else R.string.generations)
                        .lowercase(
                            Locale.getDefault()
                        )
            if (alb.media > 0) dati += " - " + alb.media + " " + context.getString(R.string.media)
                .lowercase(
                    Locale.getDefault()
                )
            return dati
        }

        /**
         * Opening the temporary Gedcom to extract info in [TreesActivity]
         */
        fun openGedcomTemporarily(treeId: Int, putInGlobal: Boolean): Gedcom? {
            val gc: Gedcom?
            if (Global.gc != null && Global.settings!!.openTree == treeId) gc = Global.gc else {
                gc = readJson(treeId)
                if (putInGlobal) {
                    Global.gc = gc // to be able to use for example F.showMainImageForPerson()
                    Global.settings!!.openTree =
                        treeId // so Global.gc and Global.settings.openTree are synchronized
                }
            }
            return gc
        }

        /**
         * Opening the Gedcom to edit everything in Family Gem
         */
        fun openGedcom(treeId: Int, savePreferences: Boolean): Boolean {
            Global.gc = readJson(treeId)
            if (Global.gc == null) return false
            if (savePreferences) {
                Global.settings!!.openTree = treeId
                Global.settings!!.save()
            }
            Global.indi = Global.settings!!.currentTree!!.root
            Global.familyNum = 0 // eventually resets it if it was > 0
            Global.shouldSave = false // eventually resets it if it was true
            return true
        }

        /**
         * Read the Json and return a Gedcom
         */
        @JvmStatic
        fun readJson(treeId: Int): Gedcom? {
            val gedcom: Gedcom?
            val file = File(Global.context!!.filesDir, "$treeId.json")
            val text = StringBuilder()
            try {
                val br = BufferedReader(FileReader(file))
                var line: String?
                while (br.readLine().also { line = it } != null) {
                    text.append(line)
                    text.append('\n')
                }
                br.close()
            } catch (e: Exception) {
                val message =
                    if (e is OutOfMemoryError) Global.context!!.getString(R.string.not_memory_tree) else e.localizedMessage
                Toast.makeText(Global.context, message, Toast.LENGTH_LONG).show()
                return null
            } catch (e: Error) {
                val message =
                    if (e is OutOfMemoryError) Global.context!!.getString(R.string.not_memory_tree) else e.localizedMessage
                Toast.makeText(Global.context, message, Toast.LENGTH_LONG).show()
                return null
            }
            var json = text.toString()
            json = updateLanguage(json)
            gedcom = JsonParser().fromJson(json)
            if (gedcom == null) {
                Toast.makeText(Global.context, R.string.no_useful_data, Toast.LENGTH_LONG).show()
                return null
            }
            // This Notifier was introduced in version 0.9.1
            // Todo: Can be removed from here in the future because tree.birthdays will never more be null
            if (Global.settings!!.getTree(treeId)!!.birthdays == null) {
                Notifier(Global.context!!, gedcom, treeId, Notifier.What.CREATE)
            }
            return gedcom
        }

        /**
         * Replace Italian with English in Json tree data
         * Introduced in Family Gem 0.8
         */
        fun updateLanguage(json: String): String {
            return json
                .replace("\"zona\":", "\"zone\":")
                .replace("\"famili\":", "\"kin\":")
                .replace("\"passato\":", "\"passed\":")
        }

        fun deleteTree(context: Context, treeId: Int) {
            val treeFile = File(context.filesDir, "$treeId.json")
            treeFile.delete()
            val mediaDir = context.getExternalFilesDir(treeId.toString())
            deleteFilesAndDirs(mediaDir)
            if (Global.settings!!.openTree == treeId) {
                Global.gc = null
            }
            Notifier(context, null, treeId, Notifier.What.DELETE)
            Global.settings!!.deleteTree(treeId)
        }

        fun deleteFilesAndDirs(fileOrDirectory: File?) {
            if (fileOrDirectory!!.isDirectory) {
                for (child in fileOrDirectory.listFiles()) deleteFilesAndDirs(child)
            }
            fileOrDirectory.delete()
        }
    }
}