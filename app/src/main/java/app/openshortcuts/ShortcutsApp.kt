package app.openshortcuts

import android.app.Activity
import android.app.Application
import android.os.Bundle
import app.openshortcuts.engine.Launchers
import app.openshortcuts.engine.Notifs
import app.openshortcuts.engine.Scheduler
import app.openshortcuts.model.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ShortcutsApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = 0
    val inForeground get() = started > 0

    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        app.openshortcuts.ui.Tour.init(this, freshInstall = Store.fresh)
        Notifs.channels(this)
        Store.onChange = { Scheduler.rescheduleAll(this, it); Launchers.sync(this, it); app.openshortcuts.engine.EventHost.current?.watchApps() }
        Scheduler.rescheduleAll(this)
        Launchers.sync(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { started++ }
            override fun onActivityStopped(a: Activity) { started-- }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }
}
