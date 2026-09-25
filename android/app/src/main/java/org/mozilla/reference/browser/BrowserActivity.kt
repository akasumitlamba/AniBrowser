/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.reference.browser

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_LONG
import mozilla.components.browser.state.state.WebExtensionState
import mozilla.components.concept.engine.EngineView
import mozilla.components.feature.intent.ext.EXTRA_SESSION_ID
import mozilla.components.lib.crash.Crash
import mozilla.components.support.base.feature.ActivityResultHandler
import mozilla.components.support.base.feature.UserInteractionHandler
import mozilla.components.support.base.log.logger.Logger
import mozilla.components.support.ktx.android.view.setupPersistentInsets
import mozilla.components.support.utils.SafeIntent
import mozilla.components.support.webextensions.WebExtensionPopupObserver
import org.mozilla.reference.browser.addons.WebExtensionActionPopupActivity
import org.mozilla.reference.browser.browser.BrowserFragment
import org.mozilla.reference.browser.browser.CrashIntegration
import org.mozilla.reference.browser.ext.components

/** Activity that holds the [BrowserFragment]. */
open class BrowserActivity : AppCompatActivity() {
    var suppressAddressBackKeyUp = false
    var addressBackHandledAt = 0L

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK &&
            event.action == android.view.KeyEvent.ACTION_UP && suppressAddressBackKeyUp) {
            suppressAddressBackKeyUp = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onResume() {
        super.onResume()
        org.mozilla.reference.browser.ani.PlaybackController.setChromeInset(0)
        org.mozilla.reference.browser.ani.PlaybackController.appVisibility(false)
        org.mozilla.reference.browser.ani.NavigationGuard.bind(this)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations && !isInPictureInPictureMode) {
            org.mozilla.reference.browser.ani.PlaybackController.appVisibility(true)
        }
    }

    override fun onPause() {
        org.mozilla.reference.browser.ani.NavigationGuard.unbind(this)
        super.onPause()
    }
    private var pipControls: org.mozilla.reference.browser.pip.PipControls? = null
    private lateinit var crashIntegration: CrashIntegration

    private val sessionId: String?
        get() = SafeIntent(intent).getStringExtra(EXTRA_SESSION_ID)

    private val webExtensionPopupObserver by lazy {
        WebExtensionPopupObserver(components.core.store, ::openPopup)
    }

    /** Returns a new instance of [BrowserFragment] to display. */
    open fun createBrowserFragment(sessionId: String?): Fragment = BrowserFragment.create()

    private var showBrowserOnResume = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // The receiver already selected/created the requested tab. Reveal it if
        // this single-task activity was left showing the tabs tray. Defer the
        // transaction until FragmentManager has resumed after saved state.
        if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND ||
            intent.action == Intent.ACTION_WEB_SEARCH) {
            showBrowserOnResume = true
        }
    }

    override fun onResumeFragments() {
        super.onResumeFragments()
        if (showBrowserOnResume) {
            showBrowserOnResume = false
            supportFragmentManager.beginTransaction()
                .replace(R.id.container, createBrowserFragment(sessionId))
                .commit()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppThemeNotActionBar)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (this is org.mozilla.reference.browser.ani.WebsiteActivity) {
            enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT))
            window.setupPersistentInsets()
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.container)) { v, insets ->
                val bars = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout())
                val keyboard = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime()).bottom
                v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard))
                androidx.core.view.WindowInsetsCompat.CONSUMED
            }
            androidx.core.view.ViewCompat.requestApplyInsets(findViewById(R.id.container))
        }

        components.notificationsDelegate.bindToActivity(this)
        pipControls = org.mozilla.reference.browser.pip.PipControls(this).also { it.start() }
        addOnPictureInPictureModeChangedListener { info ->
            supportFragmentManager.fragments.filterIsInstance<org.mozilla.reference.browser.browser.BaseBrowserFragment>()
                .forEach { it.onPictureInPictureModeChanged(info.isInPictureInPictureMode) }
        }

        if (savedInstanceState == null) {
            if (this !is org.mozilla.reference.browser.ani.WebsiteActivity && components.core.store.state.tabs.isEmpty()) {
                components.useCases.tabsUseCases.addTab("about:home", selectTab = true)
            }
            supportFragmentManager.beginTransaction().apply {
                replace(R.id.container, createBrowserFragment(sessionId))
                commit()
            }
        }

        crashIntegration =
            CrashIntegration(this, components.analytics.crashReporter) { crash ->
                onNonFatalCrash(crash)
            }
        lifecycle.addObserver(crashIntegration)

        NotificationManager.checkAndNotifyPolicy(this)
        lifecycle.addObserver(webExtensionPopupObserver)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (android.os.SystemClock.uptimeMillis() - addressBackHandledAt < 500L) return
                    supportFragmentManager.fragments.forEach {
                        if (it is UserInteractionHandler && it.onBackPressed()) {
                            return
                        }
                    }
                    finish()
                }
            },
        )
    }

    @Suppress("DEPRECATION") // ComponentActivity wants us to use registerForActivityResult
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Logger.info(
            "Activity onActivityResult received with " +
                "requestCode: $requestCode, resultCode: $resultCode, data: $data"
        )

        supportFragmentManager.fragments.forEach {
            if (it is ActivityResultHandler && it.onActivityResult(requestCode, data, resultCode)) {
                return
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        pipControls?.stop()
        super.onDestroy()
        components.notificationsDelegate.unBindActivity(this)
    }

    override fun onUserLeaveHint() {
        supportFragmentManager.fragments.forEach {
            if (it is UserInteractionHandler && it.onHomePressed()) {
                return
            }
        }

        super.onUserLeaveHint()
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet,
    ): View? =
        when (name) {
            EngineView::class.java.name -> {
                components.core.engine.createView(context, attrs).asView()
            }

            else -> {
                super.onCreateView(parent, name, context, attrs)
            }
        }

    private fun onNonFatalCrash(crash: Crash) {
        Snackbar.make(findViewById(android.R.id.content), R.string.crash_report_non_fatal_message, LENGTH_LONG)
            .setAction(R.string.crash_report_non_fatal_action) {
                crashIntegration.sendCrashReport(crash)
            }
            .show()
    }

    private fun openPopup(webExtensionState: WebExtensionState) {
        val intent = Intent(this, WebExtensionActionPopupActivity::class.java)
        intent.putExtra("web_extension_id", webExtensionState.id)
        intent.putExtra("web_extension_name", webExtensionState.name)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
    }
}
