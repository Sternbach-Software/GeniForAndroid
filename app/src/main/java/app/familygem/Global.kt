package app.familygem

import android.content.Context
import android.content.res.Configuration
import android.view.View
import androidx.multidex.MultiDexApplication
import androidx.appcompat.app.AppCompatDelegate
import org.folg.gedcom.model.Gedcom
import android.widget.Toast
import app.familygem.R
import com.google.gson.Gson
import org.apache.commons.io.FileUtils
import org.folg.gedcom.model.Media
import java.io.File
import java.io.FileNotFoundException
import java.lang.Exception
import java.util.*

class Global : MultiDexApplication() {
    /**
     * This is called when the application starts, and also when it is restarted
     */
    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        start(context)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        // Keep the app locale if system language is changed while the app is running
        val appLocale = AppCompatDelegate.getApplicationLocales()[0]
        if (appLocale != null) {
            Locale.setDefault(appLocale) // Keep the gedcom.jar library locale
            newConfig.setLocale(appLocale)
            applicationContext.resources.updateConfiguration(newConfig, null) // Keep global context
        }
        super.onConfigurationChanged(newConfig)
    }

    companion object {
        var gc: Gedcom? = null
        lateinit var context: Context
        lateinit var settings: Settings
        var indi // Id of the selected person displayed across the app
                : String? = null
        var familyNum // Which parents' family to show in the diagram, usually 0
                = 0
        var mainView: View? = null
        var repositoryOrder = 0
        var edited // There has been an editing in IndividualEditorActivity or in DetailActivity and therefore the content of the previous activities must be updated
                = false
        var shouldSave // The Gedcom content has been changed and needs to be saved
                = false

        /**
         * path where the camera app puts the photo taken
         */
        var pathOfCameraDestination: String? = null
        lateinit var croppedMedia //temporary parking of the media in the cropping phase // parcheggio temporaneo del media in fase di croppaggio
                : Media
        var gc2 // for comparison of updates
                : Gedcom? = null
        var treeId2 // id of tree2 with updates
                = 0

        fun start(context: Context?) {
            // Handle all uncaught exceptions
            Thread.setDefaultUncaughtExceptionHandler { thread: Thread?, e: Throwable ->
                if (settings!!.loadTree) {
                    settings!!.loadTree = false
                    settings!!.save()
                }
                Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
            }
            // Settings
            var settingsFile = File(context!!.filesDir, "settings.json")
            // Rename "preferenze.json" to "settings.json" (introduced in version 0.8)
            val preferencesFile = File(context.filesDir, "preferenze.json")
            if (preferencesFile.exists() && !settingsFile.exists()) {
                if (!preferencesFile.renameTo(settingsFile)) {
                    Toast.makeText(context, R.string.something_wrong, Toast.LENGTH_LONG).show()
                    settingsFile = preferencesFile
                }
            }
            try {
                var jsonString = FileUtils.readFileToString(settingsFile, "UTF-8")
                jsonString = updateSettings(jsonString)
                val gson = Gson()
                settings = gson.fromJson(jsonString, Settings::class.java)
            } catch (e: Exception) {
                // At first boot avoid to show the toast saying that settings.json doesn't exist
                if (e !is FileNotFoundException) {
                    Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                }
            }
            if (settings == null) {
                settings = Settings()
                settings!!.init()
                // Restore possibly lost trees
                for (file in context.filesDir.listFiles()) {
                    val name = file.name
                    if (file.isFile && name.endsWith(".json")) {
                        try {
                            val treeId = name.substring(0, name.lastIndexOf(".json")).toInt()
                            val mediaDir =
                                File(context.getExternalFilesDir(null), treeId.toString())
                            settings!!.trees!!.add(
                                Settings.Tree(
                                    treeId, treeId.toString(),
                                    if (mediaDir.exists()) mediaDir.path else null,
                                    0, 0, null, null, 0
                                )
                            )
                        } catch (e: Exception) {
                        }
                    }
                }
                // Some tree has been restored
                if (!settings!!.trees!!.isEmpty()) settings!!.referrer = null
                settings!!.save()
            }
            // Diagram settings were (probably) introduced in version 0.7.4
            if (settings!!.diagram == null) {
                settings!!.diagram = Settings.Diagram().init()
                settings!!.save()
            }
        }

        /**
         * Modifications to the text coming from files/settings.json
         */
        private fun updateSettings(json: String): String {
            // Version 0.8 added new settings for the diagram
            return json
                .replace("\"siblings\":true", "siblings:2,cousins:2,spouses:true")
                .replace(
                    "\"siblings\":false",
                    "siblings:0,cousins:0,spouses:true"
                ) // Italian translated to English (version 0.8)
                .replace("\"alberi\":", "\"trees\":")
                .replace("\"alberi\":", "\"trees\":")
                .replace("\"idAprendo\":", "\"openTree\":")
                .replace("\"autoSalva\":", "\"autoSave\":")
                .replace("\"caricaAlbero\":", "\"loadTree\":")
                .replace("\"esperto\":", "\"expert\":")
                .replace("\"nome\":", "\"title\":")
                .replace("\"cartelle\":", "\"dirs\":")
                .replace("\"individui\":", "\"persons\":")
                .replace("\"generazioni\":", "\"generations\":")
                .replace("\"radice\":", "\"root\":")
                .replace("\"condivisioni\":", "\"shares\":")
                .replace("\"radiceCondivisione\":", "\"shareRoot\":")
                .replace("\"grado\":", "\"grade\":")
                .replace("\"data\":", "\"dateId\":")
        }
    }
}