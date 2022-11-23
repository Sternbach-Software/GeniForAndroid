package app.familygem

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import java.lang.Exception

open class BaseActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Renew activity title when in-app language is changed
        try {
            val label = packageManager.getActivityInfo(componentName, 0).labelRes
            if (label != 0) { //TODO what does 0 mean?
                setTitle(label)
            }
        } catch (e: Exception) {
        }
    }
}