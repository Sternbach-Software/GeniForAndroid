package app.familygem

import java.lang.StringBuilder

/**
 * Shorthand wrapper for logging
 */
object s {
    @JvmStatic
    fun l(vararg objects: Any?) {
        val str = StringBuilder()
        if (objects != null) {
            for (obj in objects) str.append(obj).append(" ")
        } else str.append(null as String?)
        println(".\t$str")
        //android.util.Log.v("v", str);
    }

    fun p(word: Any?) {
        print(word)
    }
}