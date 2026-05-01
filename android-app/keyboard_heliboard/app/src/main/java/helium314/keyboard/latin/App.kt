// SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
package helium314.keyboard.latin

import android.app.Application
import android.os.Build
import helium314.keyboard.keyboard.emoji.SupportedEmojis
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.FoldableUtils
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.upgradeToolbarPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        initialize(this)
    }

    companion object {
        // used so JniUtils can access application once
        private var initialized = false
        private var app: Application? = null

        @JvmStatic
        fun initialize(application: Application) {
            if (initialized) return
            initialized = true
            DebugFlags.init(application)
            FoldableUtils.init(application)
            Settings.init(application)
            SubtypeSettings.init(application)

            val scope = CoroutineScope(Dispatchers.Default)
            scope.launch {
                SupportedEmojis.load(application)
                LayoutUtilsCustom.removeMissingLayouts(application)
                val packageInfo = application.packageManager.getPackageInfo(application.packageName, 0)
                @Suppress("DEPRECATION")
                Log.i(
                    "startup", "Starting ${application.applicationInfo.processName} version ${packageInfo.versionName} (${
                        packageInfo.versionCode
                    }) on Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})"
                )
            }

            RichInputMethodManager.init(application)
            checkVersionUpgrade(application)
            if (BuildConfig.DEBUG)
                upgradeToolbarPrefs(application.prefs())
            transferOldPinnedClips(application)
            app = application
            Defaults.initDynamicDefaults(application)
        }

        fun getApp(): Application? {
            val application = app
            app = null
            return application
        }
    }
}
