package app.familygem

import app.familygem.F.displayMediaAppList
import app.familygem.F.proposeCropping
import app.familygem.F.endImageCropping
import app.familygem.F.permissionsResult
import app.familygem.visitor.MediaListContainer
import app.familygem.MediaGalleryAdapter
import android.os.Bundle
import app.familygem.R
import app.familygem.F
import androidx.appcompat.app.AppCompatActivity
import android.content.Intent
import android.app.Activity
import android.view.*
import app.familygem.GalleryFragment
import app.familygem.U
import com.theartofdev.edmodo.cropper.CropImage
import android.view.ContextMenu.ContextMenuInfo
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.familygem.MediaFoldersActivity
import app.familygem.visitor.MediaReferences
import org.folg.gedcom.model.MediaRef
import org.folg.gedcom.model.MediaContainer
import app.familygem.visitor.FindStack
import app.familygem.Memory
import app.familygem.constant.intdefs.GALLERY_CHOOSE_MEDIA_KEY
import app.familygem.constant.intdefs.TREE_ID_KEY
import org.folg.gedcom.model.Media
import java.util.*

/**
 * List of Media
 */
class GalleryFragment : Fragment() {
    var mediaVisitor: MediaListContainer? = null
    var adapter: MediaGalleryAdapter? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        bandolo: Bundle?
    ): View? {
        setHasOptionsMenu(true)
        val view = inflater.inflate(R.layout.gallery, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.gallery_recycler)
        recyclerView.setHasFixedSize(true)
        if (Global.gc != null) {
            mediaVisitor = MediaListContainer(
                Global.gc!!,
                !requireActivity().intent.getBooleanExtra(GALLERY_CHOOSE_MEDIA_KEY, false)
            )
            Global.gc!!.accept(mediaVisitor)
            setToolbarTitle()
            val gestoreLayout: RecyclerView.LayoutManager = GridLayoutManager(
                context, 2
            )
            recyclerView.layoutManager = gestoreLayout
            adapter = MediaGalleryAdapter(mediaVisitor!!.mediaList.toList(), true)
            recyclerView.adapter = adapter
            view.findViewById<View>(R.id.fab).setOnClickListener { v: View? ->
                displayMediaAppList(
                    requireContext(), this@GalleryFragment, 4546, null
                )
            }
        }
        return view
    }

    /**
     * Leaving the activity resets the extra if no shared media has been chosen
     * // Andandosene dall'attività resetta l'extra se non è stato scelto un media condiviso
     */
    override fun onPause() {
        super.onPause()
        requireActivity().intent.removeExtra(GALLERY_CHOOSE_MEDIA_KEY)
    }

    fun setToolbarTitle() {
        (activity as AppCompatActivity?)!!.supportActionBar!!.title = mediaVisitor!!.mediaList.size
            .toString() + " " + getString(R.string.media).lowercase(Locale.getDefault())
    }

    /**
     * Update the contents of the gallery
     */
    fun recreate() {
        mediaVisitor!!.mediaList.clear()
        Global.gc!!.accept(mediaVisitor)
        adapter!!.notifyDataSetChanged()
        setToolbarTitle()
    }

    /**
     * The file fished by the file manager becomes shared media
     * // Il file pescato dal file manager diventa media condiviso
     */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == 4546) { //File taken from the supplier app is saved in Media and possibly cropped // File preso da app fornitrice viene salvato in Media ed eventualmente ritagliato
                val media = newMedia(null)
                if (proposeCropping(
                        requireContext(),
                        this,
                        data,
                        media
                    )
                ) { // if it is an image (therefore it can be cropped)
                    U.save(false, media)
                    //onRestart () + recreate () must not be triggered because then the arrival fragment is no longer the same // Non deve scattare onRestart() + recreate() perché poi il fragment di arrivo non è più lo stesso
                    return
                }
            } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
                endImageCropping(data)
            }
            U.save(true, Global.croppedMedia)
        } else if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) // if you click the back arrow in Crop Image
            Global.edited = true
    }

    // contextual Menu
    private var media: Media? = null
    override fun onCreateContextMenu(menu: ContextMenu, view: View, info: ContextMenuInfo?) {
        media = view.getTag(R.id.tag_object) as Media
        menu.add(0, 0, 0, R.string.delete)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 0) {
            val modified = deleteMedia(media, null)
            recreate()
            U.save(false, *modified)
            return true
        }
        return false
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, 0, 0, R.string.media_folders)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 0) {
            startActivity(
                Intent(context, MediaFoldersActivity::class.java)
                    .putExtra(TREE_ID_KEY, Global.settings.openTree)
            )
            return true
        }
        return false
    }

    override fun onRequestPermissionsResult(
        codice: Int,
        permission: Array<String>,
        grantResults: IntArray
    ) {
        permissionsResult(requireContext(), this, codice, permission, grantResults, null)
    }

    companion object {
        // todo bypassabile?
        @JvmStatic
        fun popularity(med: Media?): Int {
            val riferiMedia = MediaReferences(Global.gc!!, med!!, false)
            return riferiMedia.num
        }

        @JvmStatic
        fun newMedia(container: Any?): Media {
            val media = Media()
            media.id = U.newID(Global.gc!!, Media::class.java)
            media.fileTag = "FILE" // Necessary to then export the Gedcom
            Global.gc!!.addMedia(media)
            if (container != null) {
                val mediaRef = MediaRef()
                mediaRef.ref = media.id
                (container as MediaContainer).addMediaRef(mediaRef)
            }
            return media
        }

        /**
         * Detach a shared media from a container
         */
        @JvmStatic
        fun disconnectMedia(mediaId: String, container: MediaContainer) {
            val refs = container.mediaRefs.iterator()
            while (refs.hasNext()) {
                val ref = refs.next()
                if (ref.getMedia(Global.gc) == null // Possible ref to a non-existent media
                    || ref.ref == mediaId
                ) refs.remove()
            }
            if (container.mediaRefs.isEmpty()) container.mediaRefs = null
        }

        /**
         * // Delete a shared or local media and remove references in containers
         * // Return an array with modified progenitors
         *
         * // Elimina un media condiviso o locale e rimuove i riferimenti nei contenitori
         * // Restituisce un array con i capostipiti modificati
         */
        @JvmStatic
        fun deleteMedia(media: Media?, view: View?): Array<Any?> {
            val heads: MutableSet<Any?>
            if (media!!.id != null) { // media OBJECT
                Global.gc!!.media.remove(media)
                // Delete references in all containers
                val deleteMedia = MediaReferences(Global.gc!!, media, true)
                heads = deleteMedia.founders
            } else { // media LOCALE
                Global.gc?.let {
                    FindStack(
                        it,
                        media
                    )
                } //temporarily find the media stack to locate the container // trova temporaneamente la pila del media per individuare il container
                val container = Memory.secondToLastObject as MediaContainer
                container.media.remove(media)
                if (container.media.isEmpty()) container.media = null
                heads = HashSet() // set with only one parent Object
                heads.add(Memory.firstObject())
                Memory.clearStackAndRemove() // delete the stack you just created
            }
            Memory.setInstanceAndAllSubsequentToNull(media)
            if (view != null) view.visibility = View.GONE
            return heads.toTypedArray()
        }
    }
}