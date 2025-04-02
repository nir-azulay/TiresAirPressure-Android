package com.nazulay.tiresairpressuremanagement.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

/**
 * Helper class to get the current activity from Application context
 */
object ActivityProvider {
    private val currentActivity = AtomicReference<WeakReference<Activity>?>(null)

    /**
     * Lifecycle callback to track the current activity
     */
    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            currentActivity.set(WeakReference(activity))
        }

        override fun onActivityStarted(activity: Activity) {
            currentActivity.set(WeakReference(activity))
        }

        override fun onActivityResumed(activity: Activity) {
            currentActivity.set(WeakReference(activity))
        }

        override fun onActivityPaused(activity: Activity) {
            // Don't clear here as the next activity might not be resumed yet
        }

        override fun onActivityStopped(activity: Activity) {
            // Don't clear here as another activity might be in the background
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}

        override fun onActivityDestroyed(activity: Activity) {
            // Clear only if this is the currently tracked activity
            val ref = currentActivity.get()
            if (ref?.get() === activity) {
                currentActivity.set(null)
            }
        }
    }

    /**
     * Initialize the activity provider with the application
     */
    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
    }

    /**
     * Get the current activity or null if none is active
     */
    fun getCurrentActivity(): Activity? {
        return currentActivity.get()?.get()
    }
}

/**
 * Extension function to get the current activity from Application context
 */
fun Application.getActivity(): Activity? {
    return ActivityProvider.getCurrentActivity()
}

/**
 * Extension function to get the activity from any context
 */
fun Context.getActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) {
            return context
        }
        context = context.baseContext
    }
    return null
} 