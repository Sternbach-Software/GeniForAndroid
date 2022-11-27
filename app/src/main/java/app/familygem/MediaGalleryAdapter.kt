package app.familygem

import app.familygem.F.showImage
import app.familygem.GalleryFragment.Companion.popularity
import app.familygem.visitor.MediaListContainer.MediaWithContainer
import app.familygem.MediaGalleryAdapter.MediaViewHolder
import android.view.ViewGroup
import android.view.LayoutInflater
import app.familygem.R
import android.widget.TextView
import app.familygem.MediaGalleryAdapter
import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import app.familygem.ProfileActivity
import app.familygem.U
import app.familygem.F
import android.widget.ProgressBar
import android.content.Intent
import android.util.AttributeSet
import app.familygem.detail.ImageActivity
import app.familygem.Memory
import app.familygem.DetailActivity
import app.familygem.visitor.FindStack
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import app.familygem.GalleryFragment
import app.familygem.constant.intdefs.GALLERY_CHOOSE_MEDIA_KEY
import app.familygem.constant.intdefs.IS_ALONE_KEY
import app.familygem.constant.intdefs.MEDIA_ID_KEY
import org.folg.gedcom.model.Media
import org.folg.gedcom.model.Person

/**
 * Adapter for RecyclerView with media list
 */
class MediaGalleryAdapter(
    private val mediaList: List<MediaWithContainer>,
    private val details: Boolean
) : RecyclerView.Adapter<MediaViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, type: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.pezzo_media, parent, false)
        return MediaViewHolder(view, details)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int {
        return mediaList.size
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getItemViewType(position: Int): Int {
        return position
    }

    inner class MediaViewHolder(var view: View, var details: Boolean) :
        RecyclerView.ViewHolder(
            view
        ), View.OnClickListener {
        var media: Media? = null
        var container: Any? = null
        var imageView: ImageView
        var textView: TextView
        var numberView: TextView

        init {
            imageView = view.findViewById(R.id.media_img)
            textView = view.findViewById(R.id.media_testo)
            numberView = view.findViewById(R.id.media_num)
        }

        fun bind(position: Int) {
            media = mediaList[position].media
            container = mediaList[position].container
            if (details) {
                setupMedia(media!!, textView, numberView)
                view.setOnClickListener(this)
                (view.context as Activity).registerForContextMenu(view)
                view.setTag(R.id.tag_object, media)
                view.setTag(R.id.tag_contenitore, container)
                // Register context menu
                val activity = view.context as AppCompatActivity
                if (view.context is ProfileActivity) { // IndividualMediaFragment
                    activity.supportFragmentManager
                        .findFragmentByTag("android:switcher:" + R.id.profile_pager + ":0") // not guaranteed in the future
                        ?.registerForContextMenu(view)
                } else if (view.context is Principal) // GalleryFragment
                    activity.supportFragmentManager.findFragmentById(R.id.contenitore_fragment)!!
                        .registerForContextMenu(
                            view
                        ) else  // in AppCompatActivity
                    activity.registerForContextMenu(view)
            } else {
                val params = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.WRAP_CONTENT,
                    U.dpToPx(110f)
                )
                val margin = U.dpToPx(5f)
                params.setMargins(margin, margin, margin, margin)
                view.layoutParams = params
                textView.visibility = View.GONE
                numberView.visibility = View.GONE
            }
            showImage(media!!, imageView, view.findViewById(R.id.media_circolo))
        }

        override fun onClick(v: View) {
            val activity = v.context as AppCompatActivity
            // Gallery in choose mode of the media object
            // Return the id of a media object to IndividualMediaFragment
            if (activity.intent.getBooleanExtra(GALLERY_CHOOSE_MEDIA_KEY, false)) {
                val intent = Intent()
                intent.putExtra(MEDIA_ID_KEY, media!!.id)
                activity.setResult(Activity.RESULT_OK, intent)
                activity.finish()
                // Gallery in normal mode opens ImageActivity
            } else {
                val intent = Intent(v.context, ImageActivity::class.java)
                if (media!!.id != null) { // all Media records
                    Memory.setFirst(media)
                } else if (activity is ProfileActivity && container is Person // top tier media in indi
                    || activity is DetailActivity
                ) { // normal opening in the DetailActivity
                    Memory.add(media)
                } else { // from Gallery all the simple media, or from IndividualMediaFragment the media under multiple levels
                    FindStack(Global.gc!!, media!!)
                    if (activity is Principal) // Only in the Gallery
                        intent.putExtra(
                            IS_ALONE_KEY,
                            true
                        ) // so then ImageActivity shows the pantry (?)
                }
                v.context.startActivity(intent)
            }
        }
    }

    /**
     * This is just to create a RecyclerView with media icons that is transparent to clicks
     * TODO prevents scrolling in Detail though
     */
    internal class MediaIconsRecyclerView : RecyclerView {
        constructor(context: Context?) : super(context!!) {}
        constructor(context: Context?, attrs: AttributeSet?) : super(
            context!!, attrs
        ) {
        }

        constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
            context!!, attrs, defStyleAttr
        ) {
        }

        var details = false

        constructor(context: Context?, details: Boolean) : super(context!!) {
            this.details = details
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            super.onTouchEvent(e)
            return details // when false the grid does not intercept the click
        }
    }

    companion object {
        @JvmStatic
        fun setupMedia(media: Media, textView: TextView, vistaNumero: TextView) {
            var text = ""
            if (media.title != null) text = """
     ${media.title}
     
     """.trimIndent()
            if (Global.settings!!.expert && media.file != null) {
                var file = media.file
                file = file.replace('\\', '/')
                if (file.lastIndexOf('/') > -1) {
                    if (file.length > 1 && file.endsWith("/")) // removes the last slash
                        file = file.substring(0, file.length - 1)
                    file = file.substring(file.lastIndexOf('/') + 1)
                }
                text += file
            }
            if (text.isEmpty()) textView.visibility = View.GONE else {
                if (text.endsWith("\n")) text = text.substring(0, text.length - 1)
                textView.text = text
            }
            if (media.id != null) {
                vistaNumero.text = popularity(media).toString()
                vistaNumero.visibility = View.VISIBLE
            } else vistaNumero.visibility = View.GONE
        }
    }
}