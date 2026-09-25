/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

@file:Suppress("ForbiddenComment")

package org.mozilla.reference.browser

import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import mozilla.components.browser.errorpages.ErrorPages
import mozilla.components.browser.errorpages.ErrorType
import mozilla.components.concept.engine.EngineSession
import mozilla.components.concept.engine.request.RequestInterceptor
import org.mozilla.reference.browser.ext.components
import org.mozilla.reference.browser.tabs.PrivatePage

/**
 * NB, and FIXME: this class is consumed by a 'Core' component group, but itself relies on 'firefoxAccountsFeature'
 * component; this creates a circular dependency, since firefoxAccountsFeature relies on tabsUseCases which in turn
 * needs 'core' itself.
 */
class AppRequestInterceptor(private val context: Context) : RequestInterceptor {
    override fun onLoadRequest(
        engineSession: EngineSession,
        uri: String,
        lastUri: String?,
        hasUserGesture: Boolean,
        isSameDomain: Boolean,
        isRedirect: Boolean,
        isDirectNavigation: Boolean,
        isSubframeRequest: Boolean,
    ): RequestInterceptor.InterceptionResponse? {
        if (!isSubframeRequest && (uri.startsWith("http://") || uri.startsWith("https://"))) {
            org.mozilla.reference.browser.ani.SiteSettings.apply(context, engineSession, uri)
        }
        if (org.mozilla.reference.browser.ani.NavigationGuard.intercept(
                uri, lastUri, isDirectNavigation, isSubframeRequest)) {
            return RequestInterceptor.InterceptionResponse.Deny
        }

        if (uri.startsWith("anibrowser://shortcut")) {
            val parsed = android.net.Uri.parse(uri)
            val target = parsed.getQueryParameter("url")
            val title = parsed.getQueryParameter("title") ?: "Anime"
            if (!target.isNullOrBlank()) {
                org.mozilla.reference.browser.ani.WebsiteShortcut.pin(context, target, title)
                val id = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(target.toByteArray())
                    .joinToString("") { "%02x".format(it) }
                val intent = Intent(context, org.mozilla.reference.browser.ani.WebsiteActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    data = android.net.Uri.parse("anibrowser://shortcut/$id")
                    putExtra("shortcut_id", id)
                    flags = FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
            return RequestInterceptor.InterceptionResponse.Deny
        }

        return when {
            !isSubframeRequest && uri == "about:home" -> {
                RequestInterceptor.InterceptionResponse.Url("about:blank")
            }

            uri == "about:privatebrowsing" -> {
                val page = PrivatePage.createPrivateBrowsingPage(context, uri)
                RequestInterceptor.InterceptionResponse.Content(page, encoding = "base64")
            }

            uri == "about:crashes" -> {
                val intent = Intent(context, CrashListActivity::class.java)
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)

                RequestInterceptor.InterceptionResponse.Url("about:blank")
            }

            else -> {
                val accountResponse = if (uri.startsWith(org.mozilla.reference.browser.components.BackgroundServices.REDIRECT_URL)) context.components.services.accountsAuthFeature.interceptor.onLoadRequest(
                    engineSession,
                    uri,
                    lastUri,
                    hasUserGesture,
                    isSameDomain,
                    isRedirect,
                    isDirectNavigation,
                    isSubframeRequest,
                ) else null
                accountResponse ?: context.components.services.appLinksInterceptor.onLoadRequest(
                        engineSession,
                        uri,
                        lastUri,
                        hasUserGesture,
                        isSameDomain,
                        isRedirect,
                        isDirectNavigation,
                        isSubframeRequest,
                    )
            }
        }
    }

    override fun onErrorRequest(
        session: EngineSession,
        errorType: ErrorType,
        uri: String?,
    ): RequestInterceptor.ErrorResponse {
        val errorPage = ErrorPages.createUrlEncodedErrorPage(context, errorType, uri)
        return RequestInterceptor.ErrorResponse(errorPage)
    }

    override fun interceptsAppInitiatedRequests() = true
}
