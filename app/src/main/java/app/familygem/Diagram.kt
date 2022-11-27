package app.familygem

import app.familygem.F.saveDocument
import app.familygem.constant.Gender.Companion.isMale
import app.familygem.constant.Gender.Companion.isFemale
import app.familygem.F.showMainImageForPerson
import app.familygem.constant.Gender.Companion.getGender
import app.familygem.detail.FamilyActivity.Companion.getRole
import app.familygem.detail.FamilyActivity.Companion.disconnect
import android.widget.RelativeLayout
import android.animation.AnimatorSet
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import android.os.Bundle
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import android.content.Intent
import android.animation.ObjectAnimator
import android.content.Context
import androidx.constraintlayout.widget.ConstraintLayout
import android.widget.TextView
import app.familygem.detail.FamilyActivity
import org.folg.gedcom.model.Family
import android.content.DialogInterface
import android.graphics.*
import android.view.ContextMenu.ContextMenuInfo
import androidx.appcompat.app.AppCompatActivity
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import app.familygem.constant.Choice
import app.familygem.constant.Gender
import app.familygem.constant.Relation
import app.familygem.constant.intdefs.*
import graph.gedcom.*
import org.folg.gedcom.model.Person
import java.lang.Exception
import java.util.*

class Diagram : Fragment() {
    private var graph: Graph? = null
    private lateinit var moveLayout: MoveLayout
    private lateinit var box: RelativeLayout
    private var fulcrumView: GraphicPerson? = null
    private var fulcrum: Person? = null
    private var glow: FulcrumGlow? = null
    private var lines: Lines? = null
    private var backLines: Lines? = null
    private var density = 0f
    private var STROKE // Lines thickness, in pixels
            = 0
    private var DASH // Dashed lines interval
            = 0
    private var GLOW_SPACE // Space to display glow around cards
            = 0
    private lateinit var popup // Suggestion balloon
            : View
    var forceDraw = false
    private var animator: AnimatorSet? = null
    private var printPDF // We are exporting a PDF
            = false
    private val leftToRight =
        TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR
    private var firstTime = true
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        state: Bundle?
    ): View? {
        density = resources.displayMetrics.density
        STROKE = toPx(2f)
        DASH = toPx(4f)
        GLOW_SPACE = toPx(35f)
        requireActivity().findViewById<View>(R.id.toolbar).visibility =
            View.GONE // Necessary in case of backPressed after onActivityresult
        val view = inflater.inflate(R.layout.diagram, container, false)
        view.findViewById<View>(R.id.diagram_hamburger).setOnClickListener { v: View? ->
            val drawer = requireActivity().findViewById<DrawerLayout>(R.id.scatolissima)
            drawer.openDrawer(GravityCompat.START)
        }
        view.findViewById<View>(R.id.diagram_options).setOnClickListener { vista: View? ->
            val options = PopupMenu(
                requireContext(), vista!!
            )
            val menu = options.menu
            menu.add(0, 0, 0, R.string.diagram_settings)
            if (Global.gc!!.people.size > 0) menu.add(0, 1, 0, R.string.export_pdf)
            options.show()
            options.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    0 -> startActivity(Intent(context, DiagramSettings::class.java))
                    1 -> saveDocument(
                        null,
                        this,
                        Global.settings.openTree,
                        "application/pdf",
                        "pdf",
                        903
                    )
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }
        moveLayout = view.findViewById(R.id.diagram_frame)
        moveLayout.leftToRight = leftToRight
        box = view.findViewById(R.id.diagram_box)
        //box.setBackgroundColor(0x22ff0000);
        graph = graph!!.gedcom.Graph(Global.gc) // Create a diagram model
        moveLayout.graph = graph
        forceDraw = true // To be sure the diagram will be drawn

        // Fade in animation
        val alphaIn = ObjectAnimator.ofFloat(box, View.ALPHA, 1f)
        alphaIn.duration = 100
        animator = AnimatorSet()
        animator!!.play(alphaIn)
        return view
    }

    /**
     * Identify the hub to start from, show any button 'Create the first person' or start the diagram
     * Individua il fulcro da cui partire, mostra eventuale bottone 'Crea la prima persona' oppure avvia il diagramma
     */
    override fun onStart() {
        super.onStart()
        // Reasons why we must continue, especially things that have changed// Ragioni per cui bisogna proseguire, in particolare cose che sono cambiate
        if (forceDraw || fulcrum?.id != Global.indi // TODO should be tested
            || graph?.whichFamily != Global.familyNum
        ) {
            forceDraw = false
            box!!.removeAllViews()
            box!!.alpha = 0f
            val ids =
                arrayOf(Global.indi, Global.settings.currentTree!!.root, U.findRoot(Global.gc!!))
            for (id in ids) {
                fulcrum = Global.gc!!.getPerson(id)
                if (fulcrum != null) break
            }
            // Empty diagram
            if (fulcrum == null) {
                val button = LayoutInflater.from(context).inflate(R.layout.diagram_button, null)
                button.findViewById<View>(R.id.diagram_new).setOnClickListener { v: View? ->
                    startActivity(
                        Intent(
                            context, IndividualEditorActivity::class.java
                        )
                            .putExtra(PROFILE_ID_KEY, NEW_PERSON_VALUE)
                    )
                }
                SuggestionBalloon(context, button, R.string.new_person)
                if (!Global.settings.expert) (moveLayout!!.parent as View).findViewById<View>(R.id.diagram_options).visibility =
                    View.GONE
            } else {
                Global.indi = fulcrum!!.id // If anything, he reiterates it //Casomai lo ribadisce
                graph!!.maxAncestors(Global.settings.diagram!!.ancestors)
                    .maxGreatUncles(Global.settings.diagram!!.uncles)
                    .displaySpouses(Global.settings.diagram!!.spouses)
                    .maxDescendants(Global.settings.diagram!!.descendants)
                    .maxSiblingsNephews(Global.settings.diagram!!.siblings)
                    .maxUnclesCousins(Global.settings.diagram!!.cousins)
                    .showFamily(Global.familyNum)
                    .startFrom(fulcrum)
                drawDiagram()
            }
        }
    }

    /**
     * Put a view under the suggestion balloon
     */
    internal inner class SuggestionBalloon(context: Context?, childView: View?, suggestion: Int) :
        ConstraintLayout(
            context!!
        ) {
        init {
            val view = layoutInflater.inflate(R.layout.popup, this, true)
            box!!.addView(view)
            //setBackgroundColor(0x330066FF);
            val nodeParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            nodeParams.topToBottom = R.id.popup_fumetto
            nodeParams.startToStart = LayoutParams.PARENT_ID
            nodeParams.endToEnd = LayoutParams.PARENT_ID
            addView(childView, nodeParams)
            popup = view.findViewById(R.id.popup_fumetto)
            (popup.findViewById<View>(R.id.popup_testo) as TextView).setText(suggestion)
            popup.visibility = INVISIBLE
            popup.setOnTouchListener { v: View, e: MotionEvent ->
                v.performClick(); //TODO Android Studio says to call this
                if (e.action == MotionEvent.ACTION_DOWN) {
                    v.visibility = INVISIBLE
                    true
                } else false
            }
            postDelayed({
                moveLayout!!.childWidth = box!!.width
                moveLayout!!.childHeight = box!!.height
                moveLayout!!.displayAll()
                animator!!.start()
            }, 100)
            popup.postDelayed(Runnable { popup.setVisibility(VISIBLE) }, 1000)
        }

        override fun invalidate() {
            if (printPDF) {
                popup!!.visibility = GONE
                glow?.visibility = GONE
            }
        }
    }

    /**
     * Diagram initialized the first time and clicking on a card
     */
    fun drawDiagram() {

        // Place various type of graphic nodes in the box taking them from the list of nodes
        for (personNode in graph!!.personNodes) {
            if (personNode.person.id == Global.indi && !personNode.isFulcrumNode) box!!.addView(
                Asterisk(
                    context, personNode
                )
            ) else if (personNode.mini) box!!.addView(
                GraphicMiniCard(
                    context, personNode
                )
            ) else box!!.addView(GraphicPerson(context, personNode))
        }

        // Only one person in the diagram
        if (Global.gc!!.people.size == 1 && Global.gc!!.families.size == 0 && !printPDF) {

            // Put the card under the suggestion balloon
            val singleNode = box!!.getChildAt(0)
            box!!.removeView(singleNode)
            singleNode.id = R.id.tag_fulcrum
            val popupLayout: ConstraintLayout =
                SuggestionBalloon(context, singleNode, R.string.long_press_menu)

            // Add the glow to the fulcrum card
            if (fulcrumView != null) {
                box!!.post {
                    val glowParams = ConstraintLayout.LayoutParams(
                        singleNode.width + GLOW_SPACE * 2, singleNode.height + GLOW_SPACE * 2
                    )
                    glowParams.topToTop = R.id.tag_fulcrum
                    glowParams.bottomToBottom = R.id.tag_fulcrum
                    glowParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    glowParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    fulcrumView!!.metric.width = toDp(singleNode.width.toFloat())
                    fulcrumView!!.metric.height = toDp(singleNode.height.toFloat())
                    popupLayout.addView(FulcrumGlow(context), 0, glowParams)
                }
            }
        } else { // Two or more persons in the diagram or PDF print
            box!!.postDelayed(
                {

                    // Get the dimensions of each node converting from pixel to dip
                    for (i in 0 until box!!.childCount) {
                        val nodeView = box!!.getChildAt(i)
                        if (nodeView is GraphicMetric) { // To avoid ClassCastException that mysteriously happens sometimes
                            val graphic = nodeView
                            // GraphicPerson can be larger because of TextView, the child has the correct width
                            graphic.metric.width = toDp(graphic.getChildAt(0).width.toFloat())
                            graphic.metric.height = toDp(graphic.getChildAt(0).height.toFloat())
                        }
                    }
                    graph!!.initNodes() // Initialize nodes and lines

                    // Add bond nodes
                    for (bond in graph!!.bonds) {
                        box!!.addView(GraphicBond(context(), bond))
                    }
                    graph!!.placeNodes() // Calculate final position

                    // Add the lines
                    lines = Lines(context(), graph!!.lines, false)
                    box!!.addView(lines, 0)
                    backLines = Lines(context(), graph!!.backLines, true)
                    box!!.addView(backLines, 0)

                    // Add the glow
                    val fulcrumNode = fulcrumView!!.metric as PersonNode
                    val glowParams = RelativeLayout.LayoutParams(
                        toPx(fulcrumNode.width) + GLOW_SPACE * 2,
                        toPx(fulcrumNode.height) + GLOW_SPACE * 2
                    )
                    glowParams.rightMargin = -GLOW_SPACE
                    glowParams.bottomMargin = -GLOW_SPACE
                    box!!.addView(FulcrumGlow(context()), 0, glowParams)
                    displaceDiagram()
                    animator!!.start()
                    firstTime = false
                },
                if (firstTime) 500 else 50.toLong()
            ) // The first time Picasso needs time to load images so that graph has correct cards size
        }
    }

    private fun context(): Context? {
        return if (context != null) context else Global.context
    }

    /**
     * Update visible position of nodes and lines
     */
    fun displaceDiagram() {
        if (moveLayout!!.scaleDetector.isInProgress) return
        // Position of the nodes from dips to pixels
        for (i in 0 until box!!.childCount) {
            val nodeView = box!!.getChildAt(i)
            if (nodeView is GraphicMetric) {
                val graphicNode = nodeView
                val params = graphicNode.layoutParams as RelativeLayout.LayoutParams
                if (leftToRight) params.leftMargin =
                    toPx(graphicNode.metric.x) else params.rightMargin = toPx(graphicNode.metric.x)
                params.topMargin = toPx(graphicNode.metric.y)
            }
        }
        // The glow follows fulcrum
        val glowParams = glow!!.layoutParams as RelativeLayout.LayoutParams
        if (leftToRight) glowParams.leftMargin =
            toPx(fulcrumView!!.metric.x) - GLOW_SPACE else glowParams.rightMargin = toPx(
            fulcrumView!!.metric.x
        ) - GLOW_SPACE
        glowParams.topMargin = toPx(fulcrumView!!.metric.y) - GLOW_SPACE
        moveLayout!!.childWidth = toPx(graph!!.width) + box!!.paddingStart * 2
        moveLayout!!.childHeight = toPx(graph!!.height) + box!!.paddingTop * 2

        // Update lines
        lines!!.invalidate()
        backLines!!.invalidate()

        // Pan to fulcrum
        val scale = moveLayout!!.minimumScale()
        val padding = box!!.paddingTop * scale
        moveLayout!!.panTo(
            (if (leftToRight) toPx(fulcrumView!!.metric.centerX()) * scale - moveLayout!!.mWidth / 2 + padding else moveLayout!!.mWidth / 2 - toPx(
                fulcrumView!!.metric.centerX()
            ) * scale - padding).toInt(),
            (toPx(fulcrumView!!.metric.centerY()) * scale - moveLayout!!.mHeight / 2 + padding).toInt()
        )
    }

    /**
     * The glow around fulcrum card
     */
    internal inner class FulcrumGlow(context: Context?) : View(context) {
        var paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var bmf = BlurMaskFilter(toPx(25f).toFloat(), BlurMaskFilter.Blur.NORMAL)
        var extend = toPx(5f) // draw a rectangle a little bigger

        init {
            glow = this
        }

        override fun onDraw(canvas: Canvas) {
            paint.color = resources.getColor(R.color.accent)
            paint.maskFilter = bmf
            setLayerType(LAYER_TYPE_SOFTWARE, paint)
            canvas.drawRect(
                (GLOW_SPACE - extend).toFloat(), (GLOW_SPACE - extend).toFloat(), (
                        toPx(fulcrumView!!.metric.width) + GLOW_SPACE + extend).toFloat(), (
                        toPx(fulcrumView!!.metric.height) + GLOW_SPACE + extend).toFloat(), paint
            )
        }

        override fun invalidate() {
            if (printPDF) {
                visibility = GONE
            }
        }
    }

    /**
     * Node with one person or one bond
     */
    internal abstract inner class GraphicMetric(context: Context?, var metric: Metric) :
        RelativeLayout(context) {
        init {
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Card of a person
     */
    internal inner class GraphicPerson(context: Context?, personNode: PersonNode) :
        GraphicMetric(context, personNode) {
        var background: ImageView

        init {
            val person = personNode.person
            val view = layoutInflater.inflate(R.layout.diagram_card, this, true)
            val border = view.findViewById<View>(R.id.card_border)
            if (isMale(person)) border.setBackgroundResource(R.drawable.casella_bordo_maschio) else if (isFemale(
                    person
                )
            ) border.setBackgroundResource(R.drawable.casella_bordo_femmina)
            background = view.findViewById(R.id.card_background)
            if (personNode.isFulcrumNode) {
                background.setBackgroundResource(R.drawable.casella_sfondo_evidente)
                fulcrumView = this
            } else if (personNode.acquired) {
                background.setBackgroundResource(R.drawable.casella_sfondo_sposo)
            }
            showMainImageForPerson(Global.gc!!, person, view.findViewById(R.id.card_photo))
            val vistaNome = view.findViewById<TextView>(R.id.card_name)
            val nome = U.properName(person, true)
            if (nome.isEmpty() && view.findViewById<View>(R.id.card_photo).visibility == VISIBLE) vistaNome.visibility =
                GONE else vistaNome.text = nome
            val vistaTitolo = view.findViewById<TextView>(R.id.card_title)
            val titolo = U.title(person)
            if (titolo.isEmpty()) vistaTitolo.visibility = GONE else vistaTitolo.text = titolo
            val vistaDati = view.findViewById<TextView>(R.id.card_data)
            val dati = U.twoDates(person, true)
            if (dati.isEmpty()) vistaDati.visibility = GONE else vistaDati.text = dati
            if (!U.isDead(person)) view.findViewById<View>(R.id.card_mourn).visibility = GONE
            registerForContextMenu(this)
            setOnClickListener { v: View? ->
                if (person.id == Global.indi) {
                    Memory.setFirst(person)
                    startActivity(Intent(getContext(), ProfileActivity::class.java))
                } else {
                    clickCard(person)
                }
            }
        }

        override fun invalidate() {
            // Change background color for PDF export
            if (printPDF && (metric as PersonNode).acquired) {
                background.setBackgroundResource(R.drawable.casella_sfondo_sposo_stampa)
            }
        }
    }

    /**
     * Marriage with eventual year and vertical line
     */
    internal inner class GraphicBond(context: Context?, bond: Bond) : GraphicMetric(context, bond) {
        var hearth: View? = null

        init {
            val bondLayout = RelativeLayout(context)
            //bondLayout.setBackgroundColor(0x44ff00ff);
            addView(bondLayout, LayoutParams(toPx(bond.width), toPx(bond.height)))
            val familyNode = bond.familyNode
            if (bond.marriageDate == null) {
                hearth = View(context)
                hearth!!.setBackgroundResource(R.drawable.diagram_hearth)
                val diameter =
                    toPx(if (familyNode.mini) graph!!.gedcom.Util.MINI_HEARTH_DIAMETER else graph!!.gedcom.Util.HEARTH_DIAMETER.toFloat())
                val hearthParams = LayoutParams(diameter, diameter)
                hearthParams.topMargin = toPx(familyNode.centerRelY()) - diameter / 2
                hearthParams.addRule(CENTER_HORIZONTAL)
                bondLayout.addView(hearth, hearthParams)
            } else {
                val year = TextView(context)
                year.setBackgroundResource(R.drawable.diagram_year_oval)
                year.gravity = Gravity.CENTER
                year.text = GedcomDateConverter(bond.marriageDate).writeDate(true)
                year.textSize = 13f
                val yearParams = LayoutParams(
                    LayoutParams.MATCH_PARENT, toPx(
                        graph!!.gedcom.Util.MARRIAGE_HEIGHT.toFloat()
                    )
                )
                yearParams.topMargin =
                    toPx(bond.centerRelY() - graph!!.gedcom.Util.MARRIAGE_HEIGHT / 2)
                bondLayout.addView(year, yearParams)
            }
            setOnClickListener { view: View? ->
                Memory.setFirst(familyNode.spouseFamily)
                startActivity(Intent(context, FamilyActivity::class.java))
            }
        }

        override fun invalidate() {
            if (printPDF && hearth != null) {
                hearth!!.setBackgroundResource(R.drawable.diagram_hearth_print)
            }
        }
    }

    /**
     * Little ancestry or progeny card
     */
    internal inner class GraphicMiniCard(context: Context?, personNode: PersonNode) :
        GraphicMetric(context, personNode) {
        lateinit var layout: RelativeLayout

        init {
            val miniCard = layoutInflater.inflate(R.layout.diagram_minicard, this, true)
            val miniCardText = miniCard.findViewById<TextView>(R.id.minicard_text)
            miniCardText.text =
                if (personNode.amount > 100) "100+" else personNode.amount.toString()
            val sex = getGender(personNode.person)
            if (sex === Gender.MALE) miniCardText.setBackgroundResource(R.drawable.casella_bordo_maschio) else if (sex === Gender.FEMALE) miniCardText.setBackgroundResource(
                R.drawable.casella_bordo_femmina
            )
            if (personNode.acquired) {
                layout = miniCard.findViewById(R.id.minicard)
                layout.setBackgroundResource(R.drawable.casella_sfondo_sposo)
            }
            miniCard.setOnClickListener { view: View? -> clickCard(personNode.person) }
        }

        override fun invalidate() {
            if (printPDF && layout != null) {
                layout!!.setBackgroundResource(R.drawable.casella_sfondo_sposo_stampa)
            }
        }
    }

    /**
     * Replacement for another person who is actually fulcrum
     */
    internal inner class Asterisk(context: Context?, personNode: PersonNode) :
        GraphicMetric(context, personNode) {
        init {
            layoutInflater.inflate(R.layout.diagram_asterisk, this, true)
            registerForContextMenu(this)
            setOnClickListener { v: View? ->
                Memory.setFirst(personNode.person)
                startActivity(Intent(getContext(), ProfileActivity::class.java))
            }
        }
    }

    /**
     * Generate the view of lines connecting the cards
     */
    internal inner class Lines(
        context: Context?,
        var lineGroups: List<Set<Line>>,
        var dashed: Boolean
    ) : View(context) {
        var paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var paths: MutableList<Path> = ArrayList() // Each path contains many lines
        var biggestPath = graph!!.biggestPathSize
        var maxBitmap = graph!!.maxBitmapSize
        var mMatrix = Matrix()

        //int[] colors = {Color.WHITE, Color.RED, Color.CYAN, Color.MAGENTA, Color.GREEN, Color.BLACK, Color.YELLOW, Color.BLUE};
        init {
            //setBackgroundColor(0x330066ff);
            paint.style = Paint.Style.STROKE
        }

        override fun invalidate() {
            paint.color =
                resources.getColor(if (printPDF) R.color.diagram_lines_print else R.color.diagram_lines_screen)
            paths.clear() // In case of PDF print
            val width = toPx(graph!!.width).toFloat()
            var pathNum = 0 // index of paths
            // Put the lines in one or more paths
            for (lineGroup in lineGroups) {
                if (pathNum >= paths.size) paths.add(Path())
                val path = paths[pathNum]
                for (line in lineGroup) {
                    var x1 = toPx(line.x1).toFloat()
                    val y1 = toPx(line.y1).toFloat()
                    var x2 = toPx(line.x2).toFloat()
                    val y2 = toPx(line.y2).toFloat()
                    if (!leftToRight) {
                        x1 = width - x1
                        x2 = width - x2
                    }
                    path.moveTo(x1, y1)
                    if (line is CurveLine) {
                        path.cubicTo(x1, y2, x2, y1, x2, y2)
                    } else { // Horizontal or vertical line
                        path.lineTo(x2, y2)
                    }
                }
                pathNum++
            }
            // Possibly downscale paths and thickness
            var stroke = STROKE.toFloat()
            val dashIntervals = floatArrayOf(DASH.toFloat(), DASH.toFloat())
            if (biggestPath > maxBitmap) {
                val factor = maxBitmap / biggestPath
                mMatrix.setScale(factor, factor)
                for (path in paths) {
                    path.transform(mMatrix)
                }
                stroke *= factor
                dashIntervals[0] *= factor
                dashIntervals[1] *= factor
            }
            paint.strokeWidth = stroke
            if (dashed) paint.pathEffect = DashPathEffect(dashIntervals, 0F)

            // Update this view size
            val params = layoutParams as RelativeLayout.LayoutParams
            params.width = toPx(graph!!.width)
            params.height = toPx(graph!!.height)
            requestLayout()
        }

        override fun onDraw(canvas: Canvas) {
            // Possibly upscale canvas
            if (biggestPath > maxBitmap) {
                val factor = biggestPath / maxBitmap
                mMatrix.setScale(factor, factor)
                canvas.concat(mMatrix)
            }
            // Draw the paths
            //int p = 0;
            for (path in paths) {
                //paint.setColor(colors[p % colors.length]);
                canvas.drawPath(path, paint)
                //p++;
            }
        }
    }

    private fun clickCard(person: Person) {
        selectParentFamily(person)
    }

    /**
     * Ask which family to display in the diagram if fulcrum has many parent families
     */
    private fun selectParentFamily(fulcrum: Person?) {
        val families = fulcrum!!.getParentFamilies(Global.gc)
        if (families.size > 1) {
            AlertDialog.Builder(requireContext()).setTitle(R.string.which_family)
                .setItems(U.listFamilies(families)) { dialog: DialogInterface?, which: Int ->
                    completeSelect(
                        fulcrum,
                        which
                    )
                }
                .show()
        } else {
            completeSelect(fulcrum, 0)
        }
    }

    /**
     * Complete above function
     */
    private fun completeSelect(fulcrum: Person?, whichFamily: Int) {
        Global.indi = fulcrum!!.id
        Global.familyNum = whichFamily
        graph!!.showFamily(Global.familyNum)
        graph!!.startFrom(fulcrum)
        box!!.removeAllViews()
        box!!.alpha = 0f
        drawDiagram()
    }

    private fun toDp(pixels: Float): Float {
        return pixels / density
    }

    private fun toPx(dips: Float): Int {
        return (dips * density + 0.5f).toInt()
    }

    private var pers: Person? = null
    private var idPersona: String? = null
    private var parentFam // Displayed family in which the person is child
            : Family? = null
    private var spouseFam // Selected family in which the person is spouse
            : Family? = null

    override fun onCreateContextMenu(menu: ContextMenu, vista: View, info: ContextMenuInfo?) {
        var personNode: PersonNode? = null
        if (vista is GraphicPerson) personNode =
            vista.metric as PersonNode else if (vista is Asterisk) personNode =
            vista.metric as PersonNode
        pers = personNode!!.person
        if (personNode.origin != null) parentFam = personNode.origin.spouseFamily
        spouseFam = personNode.spouseFamily
        idPersona = pers!!.id
        val familyLabels = getFamilyLabels(context, pers, spouseFam)
        if (idPersona == Global.indi && pers!!.getParentFamilies(Global.gc).size > 1) menu.add(
            0,
            -1,
            0,
            R.string.diagram
        )
        if (idPersona != Global.indi) menu.add(0, 0, 0, R.string.card)
        if (familyLabels[0] != null) menu.add(0, 1, 0, familyLabels[0])
        if (familyLabels[1] != null) menu.add(0, 2, 0, familyLabels[1])
        menu.add(0, 3, 0, R.string.new_relative)
        if (U.containsConnectableIndividuals(pers)) menu.add(0, 4, 0, R.string.link_person)
        menu.add(0, 5, 0, R.string.modify)
        if (!pers!!.getParentFamilies(Global.gc).isEmpty() || !pers.getSpouseFamilies(Global.gc)
                .isEmpty()
        ) menu.add(0, 6, 0, R.string.unlink)
        menu.add(0, 7, 0, R.string.delete)
        popup.visibility = View.INVISIBLE
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val parenti = arrayOf(
            getText(R.string.parent), getText(R.string.sibling),
            getText(R.string.partner), getText(R.string.child)
        )
        val id = item.itemId
        if (id == -1) { // Diagramma per fulcro figlio in più famiglie
            if (pers!!.getParentFamilies(Global.gc).size > 2) // Più di due famiglie
                selectParentFamily(pers) else  // Due famiglie
                completeSelect(pers, if (Global.familyNum == 0) 1 else 0)
        } else if (id == 0) { // Apri scheda individuo
            Memory.setFirst(pers)
            startActivity(Intent(context, ProfileActivity::class.java))
        } else if (id == 1) { // Famiglia come figlio
            if (idPersona == Global.indi) { // Se è fulcro apre direttamente la famiglia
                Memory.setFirst(parentFam)
                startActivity(Intent(context, FamilyActivity::class.java))
            } else U.askWhichParentsToShow(requireContext(), pers, 2)
        } else if (id == 2) { // Famiglia come coniuge
            U.askWhichSpouseToShow(requireContext(), pers!!, null)
        } else if (id == 3) { // Collega persona nuova
            if (Global.settings.expert) {
                val dialog: DialogFragment =
                    NewRelativeDialog(pers, parentFam, spouseFam, true, null)
                dialog.show(requireActivity().supportFragmentManager, "scegli")
            } else {
                AlertDialog.Builder(requireContext())
                    .setItems(parenti) { dialog: DialogInterface?, quale: Int ->
                        val intent = Intent(
                            context, IndividualEditorActivity::class.java
                        )
                        intent.putExtra(PROFILE_ID_KEY, idPersona)
                        intent.putExtra(RELATIONSHIP_ID_KEY, quale + 1)
                        if (U.checkMultipleMarriages(
                                intent,
                                requireContext(),
                                null
                            )
                        ) // aggiunge 'idFamiglia' o 'collocazione'
                            return@setItems   // se perno è sposo in più famiglie, chiede a chi aggiungere un coniuge o un figlio
                        startActivity(intent)
                    }.show()
            }
        } else if (id == 4) { // Collega persona esistente
            if (Global.settings.expert) {
                val dialog: DialogFragment =
                    NewRelativeDialog(pers, parentFam, spouseFam, false, this@Diagram)
                dialog.show(requireActivity().supportFragmentManager, "scegli")
            } else {
                AlertDialog.Builder(requireContext())
                    .setItems(parenti) { dialog: DialogInterface?, quale: Int ->
                        val intent = Intent(
                            context, Principal::class.java
                        )
                        intent.putExtra(PROFILE_ID_KEY, idPersona)
                        intent.putExtra(Choice.PERSON, true)
                        intent.putExtra(RELATIONSHIP_ID_KEY, quale + 1)
                        if (U.checkMultipleMarriages(intent, requireContext(), this@Diagram)) return@setItems
                        startActivityForResult(intent, 1401)
                    }.show()
            }
        } else if (id == 5) { // Modifica
            val intent = Intent(context, IndividualEditorActivity::class.java)
            intent.putExtra(PROFILE_ID_KEY, idPersona)
            startActivity(intent)
        } else if (id == 6) { // Scollega
            /*  Todo ad esser precisi bisognerebbe usare Famiglia.scollega( sfr, sr )
				che rimuove esattamente il singolo link anziché tutti i link se una persona è linkata + volte nella stessa famiglia
			 */
            val modificate: MutableList<Family> = ArrayList()
            if (parentFam != null) {
                disconnect(idPersona!!, parentFam!!)
                modificate.add(parentFam!!)
            }
            if (spouseFam != null) {
                disconnect(idPersona!!, spouseFam!!)
                modificate.add(spouseFam!!)
            }
            ripristina()
            val modificateArr = modificate.toTypedArray()
            U.checkFamilyItem(context, { ripristina() }, false, *modificateArr)
            U.updateChangeDate(pers)
            U.save(true, *modificateArr as Array<Any>)
        } else if (id == 7) { // Elimina
            AlertDialog.Builder(requireContext()).setMessage(R.string.really_delete_person)
                .setPositiveButton(R.string.delete) { dialog: DialogInterface?, i: Int ->
                    val famiglie = ListOfPeopleFragment.deletePerson(
                        context, idPersona
                    )
                    ripristina()
                    U.checkFamilyItem(context, { ripristina() }, false, *famiglie)
                }.setNeutralButton(R.string.cancel, null).show()
        } else return false
        return true
    }

    private fun ripristina() {
        forceDraw = true
        onStart()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == AppCompatActivity.RESULT_OK) {
            // Add the relative who has been chosen in ListOfPeopleActivity
            if (requestCode == 1401) {
                val modificati = IndividualEditorActivity.addParent(
                    data!!.getStringExtra(PROFILE_ID_KEY),  // corresponds to 'idPersona', which however is canceled in case of configuration change
                    data.getStringExtra(RELATIVE_ID_KEY),
                    data.getStringExtra(FAMILY_ID_KEY),
                    data.getIntExtra(RELATIONSHIP_ID_KEY, 0),
                    data.getStringExtra(LOCATION_KEY)
                )
                U.save(true, *modificati)
            } // Export diagram to PDF
            else if (requestCode == 903) {
                // Stylize diagram for print
                printPDF = true
                for (i in 0 until box!!.childCount) {
                    box!!.getChildAt(i).invalidate()
                }
                fulcrumView!!.findViewById<View>(R.id.card_background)
                    .setBackgroundResource(R.drawable.casella_sfondo_base)
                // Create PDF
                val document = PdfDocument()
                val pageInfo = PageInfo.Builder(box!!.width, box!!.height, 1).create()
                val page = document.startPage(pageInfo)
                box!!.draw(page.canvas)
                document.finishPage(page)
                printPDF = false
                // Write PDF
                val uri = data!!.data
                try {
                    val out = requireContext().contentResolver.openOutputStream(
                        uri!!
                    )
                    document.writeTo(out)
                    out!!.flush()
                    out.close()
                } catch (e: Exception) {
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                    return
                }
                Toast.makeText(context, R.string.pdf_exported_ok, Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        /**
         * Generate the 2 family (as child and as partner) labels for contextual menu
         */
        @JvmStatic
        fun getFamilyLabels(context: Context, person: Person?, _family: Family?): Array<String?> {
            var family = _family
            val labels = arrayOf<String?>(null, null)
            val parentFams = person!!.getParentFamilies(Global.gc)
            val spouseFams = person.getSpouseFamilies(Global.gc)
            if (parentFams.size > 0) labels[0] =
                if (spouseFams.isEmpty()) context.getString(R.string.family) else context.getString(
                    R.string.family_as, getRole(person, null, Relation.CHILD, true).lowercase(
                        Locale.getDefault()
                    )
                )
            if (family == null && spouseFams.size == 1) family = spouseFams[0]
            if (spouseFams.size > 0) labels[1] =
                if (parentFams.isEmpty()) context.getString(R.string.family) else context.getString(
                    R.string.family_as, getRole(person, family, Relation.PARTNER, true).lowercase(
                        Locale.getDefault()
                    )
                )
            return labels
        }
    }
}