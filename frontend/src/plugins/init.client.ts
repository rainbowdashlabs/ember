/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {forgetLegacySession} from '~/api/sessionCookie'
import {syncThemeWithSession, useTheme} from '~/composables/useTheme'
import {forgetLegacyAiSettings} from '~/util/aiCredentials'
import {installDevErrorHandlers} from '~/util/devErrorReporter'

/**
 * Starts the browser side of the app. What an older release left in local storage for the session is
 * removed, the theme stored in this browser is painted before the first render, and the instance theme
 * is asked for without holding the render up: until it arrives, the theme the server rendered into the
 * page stays in place.
 */
export default defineNuxtPlugin((nuxtApp) => {
    forgetLegacySession()
    forgetLegacyAiSettings()

    syncThemeWithSession()
    void useTheme().initFromLocalStorage()

    const devErrorHandler = installDevErrorHandlers()
    if (devErrorHandler) {
        nuxtApp.vueApp.config.errorHandler = devErrorHandler
    }
})
