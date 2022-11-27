package app.familygem

import app.familygem.BaseActivity
import android.os.Bundle
import app.familygem.R
import androidx.appcompat.widget.SwitchCompat
import android.widget.CompoundButton
import android.widget.TextView
import android.content.DialogInterface
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import android.os.Build
import android.os.LocaleList
import android.widget.LinearLayout
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.view.View
import androidx.appcompat.app.AlertDialog
import app.familygem.TombstoneActivity
import java.util.*

class OptionsActivity : BaseActivity() {
    var languages = arrayOf<Language>(
        Language(null, 0),  // System language
        Language("cs", 100),
        Language("de", 100),
        Language("en", 100),
        Language("eo", 100),
        Language("es", 100),
        Language("fa", 100),
        Language("fr", 100),
        Language("hr", 100),
        Language("hu", 100),
        Language("in", 100),
        Language("it", 100),
        Language("iw", 100),
        Language("kn", 18),
        Language("mr", 13),
        Language("nb", 100),
        Language("nl", 100),
        Language("pl", 100),
        Language("pt", 100),
        Language("ru", 100),
        Language("sk", 100),
        Language("sr", 100),
        Language("tr", 21),
        Language("uk", 100)
    )

    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.opzioni)

        // Auto save
        val save = findViewById<SwitchCompat>(R.id.opzioni_salva)
        save.isChecked = Global.settings!!.autoSave
        save.setOnCheckedChangeListener { button: CompoundButton?, isChecked: Boolean ->
            Global.settings!!.autoSave = isChecked
            Global.settings!!.save()
        }

        // Load tree at startup
        val load = findViewById<SwitchCompat>(R.id.opzioni_carica)
        load.isChecked = Global.settings!!.loadTree
        load.setOnCheckedChangeListener { button: CompoundButton?, isChecked: Boolean ->
            Global.settings!!.loadTree = isChecked
            Global.settings!!.save()
        }

        // Expert mode
        val expert = findViewById<SwitchCompat>(R.id.opzioni_esperto)
        expert.isChecked = Global.settings!!.expert
        expert.setOnCheckedChangeListener { button: CompoundButton?, isChecked: Boolean ->
            Global.settings!!.expert = isChecked
            Global.settings!!.save()
        }
        Arrays.sort(languages)
        val textView = findViewById<TextView>(R.id.opzioni_language)
        val actual = actualLanguage
        textView.text = actual.toString()
        val languageArr = arrayOfNulls<String>(languages.size)
        for (i in languages.indices) {
            languageArr[i] = languages[i].toString()
        }
        textView.setOnClickListener { view: View ->
            AlertDialog.Builder(view.context)
                .setSingleChoiceItems(
                    languageArr,
                    Arrays.asList(*languages).indexOf(actual)
                ) { dialog: DialogInterface, item: Int ->
                    val code = languages[item].code
                    // Set app locale and store it for the future
                    val appLocale = LocaleListCompat.forLanguageTags(code)
                    AppCompatDelegate.setApplicationLocales(appLocale)
                    // Update app context configuration for this session only
                    val configuration = resources.configuration
                    if (code != null) {
                        configuration.setLocale(Locale(code))
                    } else { // Take the system locale
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            // Find the first system locale supported by this app
                            var firstSupportedLocale = Locale("en") // English default language
                            val systemLocales = Resources.getSystem().configuration.locales
                            for (i in 0 until systemLocales.size()) {
                                val sysLoc = systemLocales[i]
                                val tag = sysLoc.toLanguageTag()
                                if (Arrays.stream(languages).anyMatch { lang: Language ->
                                        lang.code != null && tag.startsWith(
                                            lang.code!!
                                        )
                                    }) {
                                    firstSupportedLocale = Locale(
                                        tag.substring(
                                            0,
                                            2
                                        )
                                    ) // Just the 2 chars language code
                                    break
                                }
                            }
                            configuration.setLocale(firstSupportedLocale)
                        } else {
                            configuration.setLocale(Resources.getSystem().configuration.locale)
                        }
                    }
                    applicationContext.resources.updateConfiguration(configuration, null)
                    // Remove switches to force KitKat to update their language
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                        val layout = findViewById<LinearLayout>(R.id.layout)
                        layout.removeView(save)
                        layout.removeView(load)
                        layout.removeView(expert)
                    }
                    dialog.dismiss()
                    if (code == null) recreate()
                }.show()
        }
        findViewById<View>(R.id.opzioni_lapide).setOnClickListener { view: View? ->
            startActivity(
                Intent(this@OptionsActivity, TombstoneActivity::class.java)
            )
        }
    }

    /**
     * Return the actual Language of the app, otherwise the "system language"
     */
    private val actualLanguage: Language
        private get() {
            val firstLocale = AppCompatDelegate.getApplicationLocales()[0]
            if (firstLocale != null) {
                for (i in 1 until languages.size) {
                    val language = languages[i]
                    if (firstLocale.toString().startsWith(language.code!!)) return language
                }
            }
            return languages[0]
        }

    inner class Language(var code: String?, var percent: Int) : Comparable<Language> {
        override fun toString(): String {
            return if (code == null) {
                // Return the string "System language" on the system locale, not on the app locale
                val config = Configuration(resources.configuration)
                config.setLocale(Resources.getSystem().configuration.locale)
                createConfigurationContext(config).getText(R.string.system_language).toString()
            } else {
                val locale = Locale(code)
                var txt = locale.getDisplayLanguage(locale)
                txt = txt.substring(0, 1).uppercase(Locale.getDefault()) + txt.substring(1)
                if (percent < 100) {
                    txt += " ($percent%)"
                }
                txt
            }
        }

        override fun compareTo(lang: Language): Int {
            return if (lang.code == null) {
                1
            } else toString().compareTo(lang.toString())
        }
    }
}