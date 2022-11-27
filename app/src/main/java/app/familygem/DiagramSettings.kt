package app.familygem

import app.familygem.BaseActivity.onCreate
import app.familygem.Settings.save
import app.familygem.BaseActivity
import android.widget.SeekBar
import android.widget.LinearLayout
import android.animation.AnimatorSet
import androidx.core.text.TextUtilsCompat
import androidx.core.view.ViewCompat
import android.os.Bundle
import app.familygem.R
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.widget.SwitchCompat
import android.widget.CompoundButton
import android.animation.ObjectAnimator
import android.view.View
import android.widget.TextView
import java.util.*

class DiagramSettings : BaseActivity() {
    private var ancestors: SeekBar? = null
    private var uncles: SeekBar? = null
    private var siblings: SeekBar? = null
    private var cousins: SeekBar? = null
    private var indicator: LinearLayout? = null
    private var anim: AnimatorSet? = null
    private val leftToRight =
        TextUtilsCompat.getLayoutDirectionFromLocale(Locale.getDefault()) == ViewCompat.LAYOUT_DIRECTION_LTR

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.diagram_settings)
        indicator = findViewById(R.id.settings_indicator)

        // Number of ancestors
        ancestors = findViewById(R.id.settings_ancestors)
        ancestors.setProgress(decode(Global.settings.diagram!!.ancestors))
        ancestors.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i < uncles!!.progress) {
                    uncles!!.progress = i
                    Global.settings.diagram!!.uncles = convert(i)
                }
                if (i == 0 && siblings!!.progress > 0) {
                    siblings!!.progress = 0
                    Global.settings.diagram!!.siblings = 0
                }
                if (i == 0 && cousins!!.progress > 0) {
                    cousins!!.progress = 0
                    Global.settings.diagram!!.cousins = 0
                }
                indicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Global.settings.diagram!!.ancestors = convert(seekBar.progress)
                save()
            }
        })

        // Number of uncles, linked to ancestors
        uncles = findViewById(R.id.settings_great_uncles)
        uncles.setProgress(decode(Global.settings.diagram!!.uncles))
        uncles.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i > ancestors.getProgress()) {
                    ancestors.setProgress(i)
                    Global.settings.diagram!!.ancestors = convert(i)
                }
                indicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Global.settings.diagram!!.uncles = convert(seekBar.progress)
                save()
            }
        })

        // Display siblings
        val spouses = findViewById<SwitchCompat>(R.id.settings_spouses)
        spouses.isChecked = Global.settings.diagram!!.spouses
        spouses.setOnCheckedChangeListener { button: CompoundButton?, active: Boolean ->
            Global.settings.diagram!!.spouses = active
            save()
        }

        // Number of descendants
        val descendants = findViewById<SeekBar>(R.id.settings_descendants)
        descendants.progress = decode(Global.settings.diagram!!.descendants)
        descendants.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                indicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Global.settings.diagram!!.descendants = convert(seekBar.progress)
                save()
            }
        })

        // Number of siblings and nephews
        siblings = findViewById(R.id.settings_siblings_nephews)
        siblings.setProgress(decode(Global.settings.diagram!!.siblings))
        siblings.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i > 0 && ancestors.getProgress() == 0) {
                    ancestors.setProgress(1)
                    Global.settings.diagram!!.ancestors = 1
                }
                indicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Global.settings.diagram!!.siblings = convert(seekBar.progress)
                save()
            }
        })

        // Number of uncles and cousins, linked to ancestors
        cousins = findViewById(R.id.settings_uncles_cousins)
        cousins.setProgress(decode(Global.settings.diagram!!.cousins))
        cousins.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, i: Int, b: Boolean) {
                if (i > 0 && ancestors.getProgress() == 0) {
                    ancestors.setProgress(1)
                    Global.settings.diagram!!.ancestors = 1
                }
                indicator(seekBar)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                Global.settings.diagram!!.cousins = convert(seekBar.progress)
                save()
            }
        })
        val alphaIn = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1f)
        alphaIn.duration = 0
        val alphaOut = ObjectAnimator.ofFloat(indicator, View.ALPHA, 1f, 0f)
        alphaOut.startDelay = 2000
        alphaOut.duration = 500
        anim = AnimatorSet()
        anim!!.play(alphaIn)
        anim!!.play(alphaOut).after(alphaIn)
        indicator.setAlpha(0f)
    }

    private fun indicator(seekBar: SeekBar) {
        val i = seekBar.progress
        (indicator!!.findViewById<View>(R.id.settings_indicator_text) as TextView).text =
            convert(i).toString()
        val width = seekBar.width - seekBar.paddingLeft - seekBar.paddingRight
        val x: Float
        x =
            if (leftToRight) seekBar.x + seekBar.paddingLeft + width / 9f * i - indicator!!.width / 2f else seekBar.x + seekBar.width + seekBar.paddingRight - width / 9f * (i + 1) - indicator!!.width / 2f
        indicator!!.x = x
        indicator!!.y = seekBar.y - indicator!!.height
        anim!!.cancel()
        anim!!.start()
    }

    /**
     * Value from preferences (1 2 3 4 5 10 20 50 100) to linear scale (1 2 3 4 5 6 7 8 9)
     */
    private fun decode(i: Int): Int {
        return if (i == 100) 9 else if (i == 50) 8 else if (i == 20) 7 else if (i == 10) 6 else i
    }

    /**
     * Linear scale value to exaggerated (scaled?) one
     */
    private fun convert(i: Int): Int {
        return if (i == 6) 10 else if (i == 7) 20 else if (i == 8) 50 else if (i == 9) 100 else i
    }

    private fun save() {
        Global.settings.save()
        Global.edited = true
    }
}