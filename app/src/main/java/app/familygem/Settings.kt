package app.familygem

import com.google.gson.Gson
import android.widget.Toast
import app.familygem.Settings.Share
import app.familygem.Settings.Birthday
import app.familygem.constant.intdefs.Grade
import org.apache.commons.io.FileUtils
import java.io.File
import java.lang.Exception
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.LinkedHashSet

/**
 * Class that represents the preferences saved in 'settings.json'
 */
class Settings {
    /**
     * It's 'start' as soon as the app is installed (i.e. when 'files/settings.json' doesn't exist)
     * If the installation comes from a share it welcomes (accepts/receives/contains) a dateId type '20191003215337'
     * Soon becomes null and stays null unless all data is deleted
     */
    var referrer: String? = null
    var trees: MutableList<Tree>? = null

    /**
     * Number of the tree currently opened. 0 means not a particular tree.
     * Must be consistent with the 'Global.gc' opened tree.
     * It is not reset by closing the tree, to be reused by 'Load last opened tree at startup'.
     */
    var openTree = 0
    var autoSave = false
    var loadTree // At startup load last opened tree TODO rename to loadPreviouslyOpenedTree
            = false
    var expert = false
    var shareAgreement = false
    var diagram: Diagram? = null

    /**
     * First boot values
     * False booleans don't need to be initialized
     */
    fun init() {
        referrer = "start"
        trees = ArrayList()
        autoSave = true
        diagram = Diagram().init()
    }

    fun max(): Int {
        var num = 0
        for (tree in trees!!) {
            if (tree.id > num) num = tree.id
        }
        return num
    }

    fun add(tree: Tree) {
        trees!!.add(tree)
    }

    fun rename(id: Int, newName: String) {
        for (tree in trees!!) {
            if (tree.id == id) {
                tree.title = newName
                break
            }
        }
        save()
    }

    fun deleteTree(id: Int) {
        for (tree in trees!!) {
            if (tree.id == id) {
                trees!!.remove(tree)
                break
            }
        }
        if (id == openTree) {
            openTree = 0
        }
        save()
    }

    fun save() {
        try {
            FileUtils.writeStringToFile(
                File(Global.context.filesDir, "settings.json"),
                Gson().toJson(this),
                "UTF-8"
            ) //TODO extract all uses/new instances of Gson to global
        } catch (e: Exception) {
            Toast.makeText(Global.context, e.localizedMessage, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * The tree currently open
     */
    val currentTree: Tree?
        get() {
            for (alb in trees!!) {
                if (alb.id == openTree) return alb
            }
            return null
        }

    fun getTree(treeId: Int): Tree? {
        /* 	Since I installed Android Studio 4.0, when I compile with minifyEnabled true
			mysteriously 'trees' here is null.
			But it is not null if AFTER there is 'trees = Global.settings.trees'
			Really incomprehensible!
		*/
        if (trees == null) {
            trees = Global.settings.trees
        }
        if (trees != null) for (tree in trees!!) {
            if (tree.id == treeId) {
                if (tree.uris == null) // ferryman ( ?? "traghettatore") added to Family Gem 0.7.15
                    tree.uris = LinkedHashSet()
                return tree
            }
        }
        return null
    }

    class Diagram {
        var ancestors = 0
        var uncles = 0
        var descendants = 0
        var siblings = 0
        var cousins = 0
        var spouses = false

        /**
         * Default values
         */
        fun init(): Diagram {
            ancestors = 3
            uncles = 2
            descendants = 3
            siblings = 2
            cousins = 1
            spouses = true
            return this
        }
    }

    class Tree internal constructor(
        var id: Int,
        var title: String,
        dir: String?,
        persons: Int,
        generations: Int,
        root: String?,
        shares: MutableList<Share>?,
        grade: Int
    ) {
        var dirs: MutableSet<String> = LinkedHashSet()
        var uris: LinkedHashSet<String>?
        var persons: Int
        var generations: Int
        var media = 0
        var root: String?

        /**
         * identification data of shares across time and space
         */
        var shares: MutableList<Share>?

        /**
         * id of the Person root of the Sharing tree
         */
        var shareRoot: String? = null

        /**
         * "grade" (degree?) of sharing
         *
         *  * 0 tree created from scratch in Italy. it stays 0 even adding main submitter, sharing it and getting news
         *  * 9 tree sent for sharing waiting to mark all submitters with 'passed'
         *  * 10 tree received via sharing in Australia. Can never return to 0
         *  * 20 tree returned to Italy proved to be a derivative of a zero (or a 10). Only if it is 10 can it become 20. If by chance it loses the status of derivative it returns 10 (never 0)
         *  * 30 derived tree from which all novelties have been extracted OR with no novelties already upon arrival (gray). Disposable
         *
         */
        @Grade
        var grade: Int
        var birthdays: MutableSet<Birthday>

        init {
            if (dir != null) dirs.add(dir)
            uris = LinkedHashSet()
            this.persons = persons
            this.generations = generations
            this.root = root
            this.shares = shares
            this.grade = grade
            birthdays = HashSet()
        }

        fun aggiungiCondivisione(share: Share) {
            if (shares == null) shares = ArrayList()
            shares!!.add(share)
        }
    }

    /**
     * The essential data of a share
     */
    class Share(
        /**
         * on compressed date and time format: YYYYMMDDhhmmss
         */
        var dateId: String,
        /**
         * Submitter id
         */
        var submitter: String
    )

    /**
     * Birthday of one person
     */
    class Birthday(// E.g. 'I123'
        var id: String, // 'John'
        var given: String, // 'John Doe III'
        var name: String, // Date of next birthday in Unix time
        var date: Long, // Turned years
        var age: Int
    ) {
        override fun toString(): String {
            val sdf: DateFormat = SimpleDateFormat("d MMM y", Locale.US)
            return "[" + name + ": " + age + " (" + sdf.format(date) + ")]"
        }
    }

    /**
     * Blueprint of the file 'settings.json' inside a backup, share or example ZIP file
     * It contains basic info of the zipped tree
     */
    internal class ZippedTree(
        var title: String,
        var persons: Int,
        var generations: Int,
        var root: String,
        var shares: List<Share>, // the destination "grade" (degree?) of the zipped tree
        var grade: Int
    ) {
        fun save(): File {
            val settingsFile = File(Global.context.cacheDir, "settings.json")
            try {
                FileUtils.writeStringToFile(settingsFile, Gson().toJson(this), "UTF-8")
            } catch (e: Exception) {
            }
            return settingsFile
        }
    }
}