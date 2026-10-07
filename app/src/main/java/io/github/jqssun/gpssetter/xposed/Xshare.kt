package io.github.jqssun.gpssetter.xposed
import android.content.SharedPreferences

// hook-side view of the settings the app mirrors into LSPosed remote preferences (see PrefManager)
class Xshare(private val prefs: SharedPreferences) {

    companion object {
        const val GROUP = "settings"
    }

    val isStarted : Boolean
    get() = prefs.getBoolean(
        "start",
        false
    )

    val getLat: Double
    get() = prefs.getFloat(
        "latitude",
        45.0000000.toFloat()
    ).toDouble()


    val getLng : Double
    get() = prefs.getFloat(
        "longitude",
        0.0000000.toFloat()
    ).toDouble()

    val isHookedSystem : Boolean
    get() = prefs.getBoolean(
        "system_hooked",
        true
    )

    val isRandomPosition :Boolean
    get() = prefs.getBoolean(
        "random_position",
        false
    )

    val accuracy : String?
    get() = prefs.getString("accuracy_level","10")

}
