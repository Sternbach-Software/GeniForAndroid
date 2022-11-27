package app.familygem

import app.familygem.F.showImage
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.navigation.NavigationView
import androidx.drawerlayout.widget.DrawerLayout
import app.familygem.R
import app.familygem.ListOfPeopleFragment
import app.familygem.FamiliesFragment
import app.familygem.GalleryFragment
import app.familygem.list.NotesFragment
import app.familygem.SourcesFragment
import app.familygem.list.RepositoriesFragment
import app.familygem.ListOfAuthorsFragment
import android.os.Bundle
import androidx.appcompat.app.ActionBarDrawerToggle
import app.familygem.U
import androidx.core.view.GravityCompat
import android.content.Intent
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import android.view.View
import app.familygem.TreesActivity
import app.familygem.NewRelativeDialog
import android.widget.TextView
import app.familygem.F
import app.familygem.visitor.NoteList
import android.widget.Toast
import android.view.View.OnLongClickListener
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import app.familygem.constant.Choice
import app.familygem.constant.intdefs.ALL_MEDIA
import app.familygem.constant.intdefs.SHARED_AND_LOCAL_MEDIA
import app.familygem.visitor.MediaList
import java.lang.Exception
import java.util.*

class Principal /*TODO Main?*/ : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {
    var drawer: DrawerLayout? = null
    var toolbar: Toolbar? = null
    var mainMenu: NavigationView? = null
    var idMenu = Arrays.asList(
        R.id.nav_diagramma, R.id.nav_persone, R.id.nav_famiglie,
        R.id.nav_media, R.id.nav_note, R.id.nav_fonti, R.id.nav_archivi, R.id.nav_autore
    )
    var fragments = Arrays.asList<Class<*>>(
        Diagram::class.java,
        ListOfPeopleFragment::class.java,
        FamiliesFragment::class.java,
        GalleryFragment::class.java,
        NotesFragment::class.java,
        SourcesFragment::class.java,
        RepositoriesFragment::class.java,
        ListOfAuthorsFragment::class.java
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.principe)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        drawer = findViewById(R.id.scatolissima)
        val toggle = ActionBarDrawerToggle(
            this, drawer, toolbar, R.string.drawer_open, R.string.drawer_close
        )
        drawer!!.addDrawerListener(toggle)
        toggle.syncState()
        mainMenu = findViewById(R.id.menu)
        mainMenu!!.setNavigationItemSelectedListener(this)
        Global.mainView = drawer
        U.ensureGlobalGedcomNotNull(Global.gc)
        setupMenu()
        if (savedInstanceState == null) {  // loads the home only the first time, not after rotating the screen
            val fragment: Fragment
            var backName: String? = null // Label to locate diagram in fragment backstack
            if (intent.getBooleanExtra(Choice.PERSON, false)) fragment =
                ListOfPeopleFragment() else if (intent.getBooleanExtra(
                    Choice.MEDIA, false
                )
            ) fragment = GalleryFragment() else if (intent.getBooleanExtra(
                    Choice.SOURCE, false
                )
            ) fragment = SourcesFragment() else if (intent.getBooleanExtra(
                    Choice.NOTE, false
                )
            ) fragment = NotesFragment() else if (intent.getBooleanExtra(
                    Choice.REPOSITORY, false
                )
            ) fragment = RepositoriesFragment() else { // normal opening
                fragment = Diagram()
                backName = "diagram"
            }
            supportFragmentManager.beginTransaction().replace(R.id.contenitore_fragment, fragment)
                .addToBackStack(backName).commit()
        }
        mainMenu!!.getHeaderView(0).findViewById<View>(R.id.menu_alberi)
            .setOnClickListener { v: View? ->
                drawer!!.closeDrawer(GravityCompat.START)
                startActivity(Intent(this@Principal, TreesActivity::class.java))
            }

        // Hides difficult menu items
        if (!Global.settings!!.expert) {
            val menu = mainMenu!!.getMenu()
            menu.findItem(R.id.nav_fonti).isVisible = false
            menu.findItem(R.id.nav_archivi).isVisible = false
            menu.findItem(R.id.nav_autore).isVisible = false
        }
    }

    /**
     * Virtually always called except onBackPressed
     */
    override fun onAttachFragment(fragment: Fragment) {
        super.onAttachFragment(fragment)
        if (fragment !is NewRelativeDialog) updateUI(fragment)
    }

    /**
     * Refresh contents when going back with backPressed()
     */
    public override fun onRestart() {
        super.onRestart()
        if (Global.edited) {
            val fragment = supportFragmentManager.findFragmentById(R.id.contenitore_fragment)
            if (fragment is Diagram) {
                fragment.forceDraw = true // So redraw the diagram
            } else if (fragment is ListOfPeopleFragment) {
                fragment.restart()
            } else if (fragment is FamiliesFragment) {
                fragment.refresh(FamiliesFragment.What.RELOAD)
            } else if (fragment is GalleryFragment) {
                fragment.recreate()
                /*} else if( fragment instanceof NotesFragment ) {
				// Doesn't work to update NotesFragment when a note is deleted
				((NotesFragment)fragment).adapter.notifyDataSetChanged();*/
            } else {
                recreate() // questo dovrebbe andare a scomparire man mano
            }
            Global.edited = false
            setupMenu() // To display the Save button and update items count
        }
    }

    /**
     * It receives a class type 'Diagram.class' and tells if it is the fragment currently visible on the scene
     */
    private fun isCurrentFragment(classe: Class<*>): Boolean {
        val current = supportFragmentManager.findFragmentById(R.id.contenitore_fragment)
        return classe.isInstance(current)
    }

    /**
     * Update title, random image, 'Save' button in menu header, and menu items count
     */
    fun setupMenu() {
        val navigation = drawer!!.findViewById<NavigationView>(R.id.menu)
        val menuHeader = navigation.getHeaderView(0)
        val imageView = menuHeader.findViewById<ImageView>(R.id.menu_immagine)
        val mainTitle = menuHeader.findViewById<TextView>(R.id.menu_titolo)
        imageView.visibility = ImageView.GONE
        mainTitle.text = ""
        if (Global.gc != null) {
            val searchMedia = MediaList(
                Global.gc!!, SHARED_AND_LOCAL_MEDIA
            )
            Global.gc!!.accept(searchMedia)
            if (searchMedia.list.size > 0) {
                var random = Random().nextInt(searchMedia.list.size)
                for (med in searchMedia.list) if (--random < 0) { // reaches -1
                    showImage(med, imageView, null)
                    imageView.visibility = ImageView.VISIBLE
                    break
                }
            }
            mainTitle.text = Global.settings!!.currentTree!!.title
            if (Global.settings!!.expert) {
                val treeNumView = menuHeader.findViewById<TextView>(R.id.menu_number)
                treeNumView.text = Global.settings!!.openTree.toString()
                treeNumView.visibility = ImageView.VISIBLE
            }
            // Put count of existing records in menu items
            val menu = navigation.menu
            for (i in 1..7) {
                var count = 0
                when (i) {
                    1 -> count = Global.gc!!.people.size
                    2 -> count = Global.gc!!.families.size
                    3 -> {
                        val mediaList = MediaList(
                            Global.gc!!, ALL_MEDIA
                        )
                        Global.gc!!.accept(mediaList)
                        count = mediaList.list.size
                    }
                    4 -> {
                        val notesList = NoteList()
                        Global.gc!!.accept(notesList)
                        count = notesList.noteList.size + Global.gc!!.notes.size
                    }
                    5 -> count = Global.gc!!.sources.size
                    6 -> count = Global.gc!!.repositories.size
                    7 -> count = Global.gc!!.submitters.size
                }
                val countView =
                    menu.getItem(i).actionView!!.findViewById<TextView>(R.id.menu_item_text)
                if (count > 0) countView.text = count.toString() else countView.visibility =
                    View.GONE
            }
        }
        // Save button
        val saveButton = menuHeader.findViewById<Button>(R.id.menu_salva)
        saveButton.setOnClickListener { view: View ->
            view.visibility = View.GONE
            U.saveJson(Global.gc, Global.settings!!.openTree)
            drawer!!.closeDrawer(GravityCompat.START)
            Global.shouldSave = false
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
        }
        saveButton.setOnLongClickListener { view: View? ->
            val popup = PopupMenu(this, view!!)
            popup.menu.add(0, 0, 0, R.string.revert)
            popup.show()
            popup.setOnMenuItemClickListener { item: MenuItem ->
                if (item.itemId == 0) {
                    TreesActivity.openGedcom(Global.settings!!.openTree, false)
                    U.askWhichParentsToShow(this, null, 0) // Simply reload the diagram
                    drawer!!.closeDrawer(GravityCompat.START)
                    //saveButton.setVisibility(View.GONE);
                    Global.edited = false
                    Global.shouldSave = false
                    setupMenu()
                }
                true
            }
            true
        }
        saveButton.visibility = if (Global.shouldSave) View.VISIBLE else View.GONE
    }

    /**
     * Highlight menu item and show/hide toolbar
     */
    fun updateUI(fragment: Fragment?) {
        var fragment = fragment
        if (fragment == null) fragment =
            supportFragmentManager.findFragmentById(R.id.contenitore_fragment)
        if (fragment != null) {
            val numFram = fragments.indexOf(fragment.javaClass)
            if (mainMenu != null) mainMenu!!.setCheckedItem(idMenu[numFram])
            if (toolbar == null) toolbar = findViewById(R.id.toolbar)
            if (toolbar != null) toolbar!!.visibility =
                if (numFram == 0) View.GONE else View.VISIBLE
        }
    }

    override fun onBackPressed() {
        if (drawer!!.isDrawerOpen(GravityCompat.START)) {
            drawer!!.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
            if (supportFragmentManager.backStackEntryCount == 0) {
                // Makes Trees go back instead of reviewing the first backstack diagram
                super.onBackPressed()
            } else updateUI(null)
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        var fragment: Fragment? = null
        try {
            fragment = fragments[idMenu.indexOf(item.itemId)].newInstance() as Fragment
        } catch (e: Exception) {
        }
        if (fragment != null) {
            if (fragment is Diagram) {
                var whatToOpen = 0 // Show diagram without asking about multiple parents
                // If I'm already in diagram and I click Diagram, show root person
                if (isCurrentFragment(Diagram::class.java)) {
                    Global.indi = Global.settings!!.currentTree!!.root
                    whatToOpen = 1 // Possibly ask about multiple parents
                }
                U.askWhichParentsToShow(this, Global.gc!!.getPerson(Global.indi), whatToOpen)
            } else {
                val fm = supportFragmentManager
                // Remove previous fragment from history if it is the same one we are about to see
                if (isCurrentFragment(fragment.javaClass)) fm.popBackStack()
                fm.beginTransaction().replace(R.id.contenitore_fragment, fragment)
                    .addToBackStack(null).commit()
            }
        }
        drawer!!.closeDrawer(GravityCompat.START)
        return true
    }

    /**
     * Automatically open the 'Sort by' sub-menu
     */
    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        val item0 = menu.getItem(0)
        if (item0.title == getString(R.string.order_by)) {
            item0.isVisible = false // a little hack to prevent options menu to appear
            Handler().post {
                item0.isVisible = true
                menu.performIdentifierAction(item0.itemId, 0)
            }
        }
        return super.onMenuOpened(featureId, menu)
    }
}