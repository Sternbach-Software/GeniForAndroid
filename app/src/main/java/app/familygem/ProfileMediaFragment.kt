package app.familygem

import app.familygem.GalleryFragment.Companion.disconnectMedia
import app.familygem.GalleryFragment.Companion.deleteMedia
import app.familygem.F.showMainImageForPerson
import app.familygem.visitor.MediaListContainer
import android.os.Bundle
import android.view.*
import app.familygem.R
import android.widget.LinearLayout
import app.familygem.MediaGalleryAdapter
import android.view.ContextMenu.ContextMenuInfo
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.familygem.visitor.MediaListContainer.MediaWithContainer
import app.familygem.U
import app.familygem.GalleryFragment
import org.folg.gedcom.model.MediaContainer
import app.familygem.F
import app.familygem.ProfileFactsFragment
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Person

/**
 * Photo tab
 */
class ProfileMediaFragment : Fragment() {
    var person: Person? = null
    var mediaListContainer: MediaListContainer? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val vistaMedia = inflater.inflate(R.layout.individuo_scheda, container, false)
        if (Global.gc != null) {
            val layout = vistaMedia.findViewById<LinearLayout>(R.id.contenuto_scheda)
            person = Global.gc!!.getPerson(Global.indi)
            if (person != null) {
                mediaListContainer = MediaListContainer(Global.gc!!, true)
                person!!.accept(mediaListContainer)
                val recyclerView = RecyclerView(layout.context)
                recyclerView.setHasFixedSize(true)
                val layoutManager: RecyclerView.LayoutManager = GridLayoutManager(
                    context, 2
                )
                recyclerView.layoutManager = layoutManager
                val adapter = MediaGalleryAdapter(mediaListContainer!!.mediaList.toMutableList(), true)
                recyclerView.adapter = adapter
                layout.addView(recyclerView)
            }
        }
        return vistaMedia
    }

    // context Menu
    var media: Media? = null

    /**
     * The images are not only of [.person], but also of its subordinates [org.folg.gedcom.model.EventFact], [org.folg.gedcom.model.SourceCitation] ...
     */
    var container: Any? = null
    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        media = view.getTag(R.id.tag_object) as Media
        container = view.getTag(R.id.tag_contenitore)
        if (mediaListContainer!!.mediaList.size > 1 && media!!.primary == null) menu.add(
            0,
            0,
            0,
            R.string.primary_media
        )
        if (media!!.id != null) menu.add(0, 1, 0, R.string.unlink)
        menu.add(0, 2, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == 0) { // Principal
            for ((media1) in mediaListContainer!!.mediaList)  // It resets them all then marks this.person
                media1.primary = null
            media!!.primary = "Y"
            if (media!!.id != null) // To update the change date in the Media record rather than in the Person
                U.save(true, media) else U.save(true, person)
            refresh()
            return true
        } else if (id == 1) { // Scollega
            disconnectMedia(media!!.id, (container as MediaContainer?)!!)
            U.save(true, person)
            refresh()
            return true
        } else if (id == 2) { // Delete
            val capi = deleteMedia(media, null)
            U.save(true, *capi)
            refresh()
            return true
        }
        return false
    }

    /**
     * Refresh the contents of the Media snippet
     */
    fun refresh() {
        // refill the fragment
        val fragmentManager = requireActivity().supportFragmentManager
        fragmentManager.beginTransaction().detach(this).commit()
        fragmentManager.beginTransaction().attach(this).commit()
        showMainImageForPerson(
            Global.gc!!,
            person!!,
            requireActivity().findViewById(R.id.person_image)
        )
        showMainImageForPerson(
            Global.gc!!,
            person!!,
            requireActivity().findViewById(R.id.profile_background)
        )
        // Events tab
        val eventsTab =
            requireActivity().supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.profile_pager + ":1") as ProfileFactsFragment?
        eventsTab!!.refresh()
    }
}