package app.familygem

import app.familygem.Comparison.reset
import app.familygem.F.mediaPath
import app.familygem.F.nextAvailableFileName
import android.os.Bundle
import androidx.cardview.widget.CardView
import android.widget.TextView
import android.content.Intent
import app.familygem.visitor.NoteContainers
import app.familygem.visitor.MediaContainers
import app.familygem.visitor.ListOfSourceCitations
import android.content.DialogInterface
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import app.familygem.constant.intdefs.*
import app.familygem.visitor.MediaList
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.*
import java.io.File
import java.io.IOException

/**
 * Final activity when importing news in an existing tree
 */
class ConfirmationActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.conferma)
        if (Comparison.list.isNotEmpty()) {

            // Old tree
            val card = findViewById<CardView>(R.id.conferma_vecchio)
            val tree = Global.settings.getTree(Global.settings.openTree)
            (card.findViewById<View>(R.id.confronto_titolo) as TextView).text = tree!!.title
            val txt = TreesActivity.writeData(this, tree)
            (card.findViewById<View>(R.id.confronto_testo) as TextView).text = txt
            card.findViewById<View>(R.id.confronto_data).visibility = View.GONE
            var numAdded = 0
            var numReplaced = 0
            var numDeleted = 0
            for (front in Comparison.list) {
                when (front.destiny) {
                    OBJ_2_ADDED -> numAdded++
                    OBJ_2_REPLACES_OBJ -> numReplaced++
                    OBJ_DELETED -> numDeleted++
                }
            }
            val text =
                getString(R.string.accepted_news, numAdded + numReplaced + numDeleted, numAdded, numReplaced, numDeleted)
            (findViewById<View>(R.id.conferma_testo) as TextView).text = text
            findViewById<View>(R.id.conferma_annulla).setOnClickListener { v: View? ->
                reset()
                startActivity(Intent(this@ConfirmationActivity, TreesActivity::class.java))
            }
            findViewById<View>(R.id.conferma_ok).setOnClickListener { v: View? ->
                //Change the id and all refs to objects with canBothAddAndReplace and destiny to add // Modifica l'id e tutti i ref agli oggetti con doppiaOpzione e destino da aggiungere
                var changed = false
                for (front in Comparison.list) {
                    if (front.canBothAddAndReplace && front.destiny == OBJ_2_ADDED) {
                        changed = true
                        var newID: String
                        when (front.type) {
                            SHARED_NOTE -> {
                                newID = maxID(Note::class.java)
                                val n2 = front.object2 as Note
                                NoteContainers(
                                    Global.gc2,
                                    n2,
                                    newID
                                ) // updates all refs to the note
                                n2.id = newID // then update the note id
                            }
                            SUBMITTER -> {
                                newID = maxID(Submitter::class.java)
                                (front.object2 as Submitter?)!!.id = newID
                            }
                            REPOSITORY -> {
                                newID = maxID(Repository::class.java)
                                val repo2 = front.object2 as Repository?
                                for (source in Global.gc2.sources) if (source.repositoryRef?.ref == repo2!!.id) source.repositoryRef.ref =
                                    newID
                                repo2!!.id = newID
                            }
                            SHARED_MEDIA_TYPE -> {
                                newID = maxID(Media::class.java)
                                val m2 = front.object2 as Media?
                                MediaContainers(Global.gc2, m2!!, newID)
                                m2.id = newID
                            }
                            SOURCE_TYPE -> {
                                newID = maxID(Source::class.java)
                                val s2 = front.object2 as Source?
                                val sourceCitations = ListOfSourceCitations(Global.gc2, s2!!.id)
                                for (sc in sourceCitations.list) sc.citation!!.ref = newID
                                s2.id = newID
                            }
                            PERSON_TYPE -> {
                                newID = maxID(Person::class.java)
                                val p2 = front.object2 as Person?
                                for (fam in Global.gc2.families) {
                                    for (sr in fam.husbandRefs) if (sr.ref == p2!!.id) sr.ref =
                                        newID
                                    for (sr in fam.wifeRefs) if (sr.ref == p2!!.id) sr.ref = newID
                                    for (cr in fam.childRefs) if (cr.ref == p2!!.id) cr.ref = newID
                                }
                                p2!!.id = newID
                            }
                            FAMILY_TYPE -> {
                                newID = maxID(Family::class.java)
                                val f2 = front.object2 as Family?
                                for (per in Global.gc2.people) {
                                    for (pfr in per.parentFamilyRefs) if (pfr.ref == f2!!.id) pfr.ref =
                                        newID
                                    for (sfr in per.spouseFamilyRefs) if (sfr.ref == f2!!.id) sfr.ref =
                                        newID
                                }
                                f2!!.id = newID
                            }
                        }
                    }
                }
                if (changed) U.saveJson(Global.gc2, Global.treeId2)

                // Regular addition / replacement / deletion of records from tree2 to tree
                for (front in Comparison.list) {
                    when (front.type) {
                        SHARED_NOTE -> {
                            if (front.destiny.isObj1Removed) Global.gc.notes.remove(front.object1)
                            if (front.destiny.isObj2Added) {
                                Global.gc.addNote(front.object2 as Note?)
                                copyAllFiles(front.object2)
                            }
                        }
                        SUBMITTER -> {
                            if (front.destiny.isObj1Removed) Global.gc.submitters.remove(front.object1)
                            if (front.destiny.isObj2Added) Global.gc.addSubmitter(front.object2 as Submitter?)
                        }
                        REPOSITORY -> {
                            if (front.destiny.isObj1Removed) Global.gc.repositories.remove(front.object1)
                            if (front.destiny.isObj2Added) {
                                Global.gc.addRepository(front.object2 as Repository?)
                                copyAllFiles(front.object2)
                            }
                        }
                        SHARED_MEDIA_TYPE -> {
                            if (front.destiny.isObj1Removed) Global.gc.media.remove(front.object1)
                            if (front.destiny.isObj2Added) {
                                Global.gc.addMedia(front.object2 as Media?)
                                checkIfShouldCopyFiles(front.object2 as Media?)
                            }
                        }
                        SOURCE_TYPE -> {
                            if (front.destiny.isObj1Removed) Global.gc.sources.remove(front.object1)
                            if (front.destiny.isObj2Added) {
                                Global.gc.addSource(front.object2 as Source?)
                                copyAllFiles(front.object2)
                            }
                        }
                        PERSON_TYPE -> {
                            if (front.destiny.isObj1Removed) Global.gc.people.remove(front.object1)
                            if (front.destiny.isObj2Added) {
                                Global.gc.addPerson(front.object2 as Person?)
                                copyAllFiles(front.object2)
                            }
                        }
                        FAMILY_TYPE -> {
                            if (front.destiny.isObj1Removed) Global.gc.families.remove(front.object1)
                            if (front.destiny.isObj2Added) {
                                Global.gc.addFamily(front.object2 as Family?)
                                copyAllFiles(front.object2)
                            }
                        }
                    }
                }
                U.saveJson(Global.gc, Global.settings.openTree)

                // If he has done everything he proposes to delete the imported tree (??)//Se ha fatto tutto propone di eliminare l'albero importato
                val allOK = Comparison.list.none { it.destiny == NOTHING }
                if (allOK) {
                    Global.settings.getTree(Global.treeId2)!!.grade = NO_NOVELTIES
                    Global.settings.save()
                    AlertDialog.Builder(this@ConfirmationActivity)
                        .setMessage(R.string.all_imported_delete)
                        .setPositiveButton(android.R.string.ok) { _: DialogInterface?, _: Int ->
                            TreesActivity.deleteTree(this, Global.treeId2)
                            done()
                        }.setNegativeButton(R.string.no) { _: DialogInterface?, _: Int -> done() }
                        .setOnCancelListener { done() }.show()
                } else done()
            }
        } else onBackPressed()
    }

    /**
     * Opens the tree list
     */
    fun done() {
        reset()
        startActivity(Intent(this, TreesActivity::class.java))
    }

    /**
     * Calculate the highest id for a certain class by comparing new and old tree
     * Calcola l'id più alto per una certa classe confrontando albero nuovo e vecchio
     */
    private fun maxID(clazz: Class<*>?): String {
        val id = U.newID(Global.gc, clazz) // new id against old tree records
        val id2 = U.newID(Global.gc2, clazz) // and of the new tree
        return if (id.substring(1).toInt() > id2.substring(1).toInt()) // removes the initial letter
            id else id2
    }

    /**
     * If a new object has media, consider copying the files to the old tree image folder
     * still update the link in the Media
     *
     * Se un object nuovo ha dei media, valuta se copiare i file nella cartella immagini dell'albero vecchio
     * comunque
     * aggiorna il collegamento nel Media
     */
    private fun copyAllFiles(any: Any?) {
        val searchMedia = MediaList(Global.gc2, LOCAL_MEDIA)
        (any as Visitable).accept(searchMedia)
        for (media in searchMedia.list) {
            checkIfShouldCopyFiles(media)
        }
    }

    private fun checkIfShouldCopyFiles(media: Media?) {
        val path = mediaPath(Global.treeId2, media!!)
        if (path != null) {
            val filePath = File(path)
            val memoryDir =
                getExternalFilesDir(Global.settings.openTree.toString()) // it should stay out of the loop but oh well //dovrebbe stare fuori dal loop ma vabè
            val nameFile = filePath.name
            val twinFile = File(memoryDir!!.absolutePath, nameFile)
            if (twinFile.isFile // if the corresponding file already exists
                && twinFile.lastModified() == filePath.lastModified() // and have the same date
                && twinFile.length() == filePath.length()
            ) { // and the same size
                // Then use the already existing file
                media.file = twinFile.absolutePath
            } else { // Otherwise copy the new file
                val destinationFile = nextAvailableFileName(memoryDir.absolutePath, nameFile)
                try {
                    FileUtils.copyFile(filePath, destinationFile)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                media.file = destinationFile.absolutePath
            }
        }
    }

    override fun onOptionsItemSelected(i: MenuItem): Boolean {
        onBackPressed()
        return true
    }
}