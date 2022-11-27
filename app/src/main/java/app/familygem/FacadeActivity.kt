package app.familygem

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import app.familygem.R
import androidx.appcompat.app.AppCompatDelegate
import android.content.Intent
import app.familygem.U
import app.familygem.FacadeActivity
import app.familygem.TreesActivity
import org.apache.commons.net.ftp.FTPClient
import app.familygem.NewTreeActivity
import android.app.Activity
import android.content.Context
import android.view.View
import app.familygem.constant.intdefs.OPEN_TREE_AUTOMATICALLY_KEY
import java.io.FileOutputStream
import java.lang.Exception

class FacadeActivity : AppCompatActivity() {
    override fun onCreate(bundle: Bundle?) {
        super.onCreate(bundle)
        setContentView(R.layout.facciata)

        // Set app locale for application context and resources (localized gedcom.jar library)
        val locale =
            AppCompatDelegate.getApplicationLocales()[0] // Find app locale, or null if not existing
        if (locale != null) {
            val config = resources.configuration
            config.setLocale(locale)
            applicationContext.resources.updateConfiguration(
                config,
                null
            ) // Change locale both for static methods and jar library
        }

        /*
		Opening after clicking on various types of links:
		https://www.familygem.app/share.php?tree=20190802224208
			Short message
			Clicked in Chrome in old Android opens the choice of the app including Family Gem to directly import the tree
			Normally opens the site sharing page
		intent: //www.familygem.app/condivisi/20200218134922.zip#Intent; scheme = https; end
			Official link on the site's sharing page
			it is the only one that seems to be sure that it works, in Chrome, in the browser inside Libero, in the L90 Browser
		https://www.familygem.app/condivisi/20190802224208.zip
			Direct URL to the zip
			It works in old android, in new ones simply the file is downloaded

		Apertura in seguito al click su vari tipi di link:
		https://www.familygem.app/share.php?tree=20190802224208
			Messaggio breve
			Cliccato in Chrome nei vecchi Android apre la scelta dell'app tra cui Family Gem per importare direttamente l'albero
			Normalmente apre la pagina di condivisione del sito
		intent://www.familygem.app/condivisi/20200218134922.zip#Intent;scheme=https;end
			Link ufficiale nella pagina di condivisione del sito
			è l'unico che sembra avere certezza di funzionare, in Chrome, nel browser interno a Libero, nel Browser L90
		https://www.familygem.app/condivisi/20190802224208.zip
			URL diretto allo zip
			Funziona nei vecchi android, nei nuovi semplicemente il file viene scaricato
		*/
        val intent = intent
        val uri = intent.data
        // By opening the app from Task Manager, avoid re-importing a newly imported shared tree
        val fromHistory =
            intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY
        if (uri != null && !fromHistory) {
            val dataId: String?
            dataId = if (uri.path == "/share.php") // click on the first message received
                uri.getQueryParameter("tree") else if (uri.lastPathSegment!!.endsWith(".zip")) // click on the invitation page
                uri.lastPathSegment!!.replace(".zip", "") else {
                U.toast(this, R.string.cant_understand_uri)
                return
            }
            if (!BuildConfig.utenteAruba.isEmpty()) {
                // It does not need to apply for permissions
                downloadShared(this, dataId, null)
            }
        } else {
            val treesIntent = Intent(this, TreesActivity::class.java)
            // Open last tree at startup
            if (Global.settings.loadTree) {
                treesIntent.putExtra(OPEN_TREE_AUTOMATICALLY_KEY, true)
                treesIntent.flags =
                    Intent.FLAG_ACTIVITY_NO_ANIMATION // perhaps ineffective but so be it
            }
            startActivity(treesIntent)
        }
    }

    companion object {
        /**
         * Connects to the server and downloads the zip file to import it
         */
        @JvmStatic
        fun downloadShared(context: Context, idData: String?, wheel: View?) {
            if (wheel != null) wheel.visibility = View.VISIBLE
            // A new Thread is needed to asynchronously download a file
            Thread {
                try {
                    val client = FTPClient() //TODO refactor to use Retrofit
                    client.connect("89.46.104.211")
                    client.enterLocalPassiveMode()
                    client.login(BuildConfig.utenteAruba, BuildConfig.passwordAruba)
                    // Todo: Maybe you could use the download manager so that you have the file also listed in 'Downloads'
                    val zipPath = context.externalCacheDir.toString() + "/" + idData + ".zip"
                    val fos = FileOutputStream(zipPath)
                    val path = "/www.familygem.app/condivisi/$idData.zip"
                    val input = client.retrieveFileStream(path)
                    if (input != null) {
                        val data = ByteArray(1024)
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            fos.write(data, 0, count)
                        }
                        fos.close()
                        if (client.completePendingCommand() && NewTreeActivity.unZip(
                                context,
                                zipPath,
                                null
                            )
                        ) {
                            //If the tree was downloaded with the install referrer // Se l'albero è stato scaricato con l'install referrer
                            if (Global.settings.referrer != null && Global.settings.referrer == idData) {
                                Global.settings.referrer = null
                                Global.settings.save()
                            }
                        } else { // Failed decompression of downloaded ZIP (e.g. corrupted file)
                            downloadFailed(
                                context,
                                context.getString(R.string.backup_invalid),
                                wheel
                            )
                        }
                    } else  // Did not find the file on the server
                        downloadFailed(context, context.getString(R.string.something_wrong), wheel)
                    client.logout()
                    client.disconnect()
                } catch (e: Exception) {
                    downloadFailed(context, e.localizedMessage, wheel)
                }
            }.start()
        }

        /**
         * Negative conclusion of the above method
         */
        fun downloadFailed(context: Context, message: String?, wheel: View?) {
            U.toast(context as Activity, message)
            if (wheel != null) context.runOnUiThread {
                wheel.visibility = View.GONE
            } else context.startActivity(Intent(context, TreesActivity::class.java))
        }
    }
}