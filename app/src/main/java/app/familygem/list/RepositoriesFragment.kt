package app.familygem.list

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.view.ContextMenu.ContextMenuInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import app.familygem.Global
import app.familygem.Memory
import app.familygem.R
import app.familygem.U
import app.familygem.constant.Choice
import app.familygem.constant.intdefs.*
import app.familygem.detail.RepositoryActivity
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.model.Repository
import org.folg.gedcom.model.RepositoryRef
import org.folg.gedcom.model.Source
import java.util.*

/**
 * List of repositories
 */
class RepositoriesFragment : Fragment() {

    lateinit var layout: LinearLayout
    lateinit var repository: Repository

    @SortOrder
    var order = UNDEFINED_SORT_ORDER

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        bundle: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.scrollview, container, false)
        layout = view.findViewById(R.id.scrollview_layout)
        view.findViewById<View>(R.id.fab).setOnClickListener {
            newRepository(
                context, null
            )
        }
        return view
    }

    override fun onStart() {
        super.onStart()
        if (Global.gc != null) {
            val repos = Global.gc.repositories
            (activity as AppCompatActivity?)?.supportActionBar?.title =
                (repos.size.toString() + " "
                        + getString(if (repos.size == 1) R.string.repository else R.string.repositories).lowercase(
                    Locale.getDefault()
                ))
            if (repos.size > 1) setHasOptionsMenu(true)
            repos.sortWith { r1: Repository, r2: Repository ->
                when (order) {
                    SORT_BY_ID -> U.extractNum(r1.id) - U.extractNum(r2.id)
                    SORT_BY_NAME -> r1.name.compareTo(r2.name, ignoreCase = true)
                    SORT_BY_SOURCE_COUNT -> countSources(Global.gc, r2) - countSources(
                        Global.gc,
                        r1
                    )
                    else /*UNDEFINED_SORT_ORDER*/ -> 0 //TODO why should they all be equal if a sort order wasn't defined?? There should be an intentional default behavior
                }
            }
            layout.removeAllViews()
            for (repo in repos) {
                val repoView = layoutInflater.inflate(R.layout.scrollview_item, layout, false)
                layout.addView(repoView)
                repoView.findViewById<TextView>(R.id.item_name).text = repo.name
                repoView.findViewById<TextView>(R.id.item_num).text =
                    countSources(Global.gc, repo).toString()
                repoView.setOnClickListener {
                    if (activity?.intent?.getBooleanExtra(
                            Choice.REPOSITORY, false
                        ) == true
                    ) {
                        val intent = Intent()
                        intent.putExtra(REPO_ID_KEY, repo.id)
                        activity?.setResult(Activity.RESULT_OK, intent)
                        activity?.finish()
                    } else {
                        Memory.setFirst(repo)
                        startActivity(Intent(context, RepositoryActivity::class.java))
                    }
                }
                registerForContextMenu(repoView)
                repoView.tag = repo

                // Extension "fonti" is removed from version 0.9.1
                if (repo.getExtension(SOURCE_EXTENSION_KEY) != null) repo.putExtension(SOURCE_EXTENSION_KEY, null)
                if (repo.extensions.isEmpty()) repo.extensions = null
            }
        }
    }

    override fun onPause() {
        super.onPause()
        activity?.intent?.removeExtra(Choice.REPOSITORY)
    }

    /**
     * overflow menu in toolbar
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        val subMenu = menu.addSubMenu(R.string.order_by)
        if (Global.settings.expert) subMenu.add(0, SORT_BY_ID, 0, R.string.id)
        subMenu.add(0, SORT_BY_NAME, 0, R.string.name)
        subMenu.add(0, SORT_BY_SOURCE_COUNT, 0, R.string.sources_number)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId !in 1..3) return false
        order = item.itemId
        onStart()
        return true
    }

    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        repository = view.tag as Repository
        menu.add(0, OPTION_DELETE, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == OPTION_DELETE) {
            val sources = delete(repository)
            U.save(false, *(sources.toTypedArray()))
            activity?.recreate()
            return true
        }
        return false
    }

    companion object {
        /**
         * Count how many sources are present in a repository
         */
        fun countSources(gedcom: Gedcom, repo: Repository): Int =
            gedcom.sources.count { it.repositoryRef?.ref == repo.id }

        /**
         * Create a new repository, optionally linking a source to it
         */
        @JvmStatic
        fun newRepository(context: Context?, source: Source?) {
            val repo = Repository()
            repo.id = U.newID(Global.gc, Repository::class.java)
            repo.name = ""
            Global.gc.addRepository(repo)
            if (source != null) {
                val repoRef = RepositoryRef()
                repoRef.ref = repo.id
                source.repositoryRef = repoRef
            }
            U.save(true, repo)
            Memory.setFirst(repo)
            context?.startActivity(Intent(context, RepositoryActivity::class.java))
        }

        /**
         * Remove the archive and refs from sources where the archive is mentioned
         * According to the Gedcom 5.5 specifications, the FS and Family Historian library a SOUR provides only one Ref to a REPO
         * Conversely, according to Gedcom 5.5.1, it can have multiple Refs to archives
         * @return an array of modified Sources
         */
        fun delete(repo: Repository): List<Source> {
            val sources = mutableListOf<Source>()
            for (sour in Global.gc.sources) if (sour.repositoryRef?.ref == repo.id) {
                sour.repositoryRef = null
                sources.add(sour)
            }
            Global.gc.repositories.remove(repo)
            Memory.setInstanceAndAllSubsequentToNull(repo)
            return sources
        }
    }
}