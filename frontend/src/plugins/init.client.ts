/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {initTokenRefresh} from '~/api/client'
import {useTheme} from '~/composables/useTheme'
import {installDevErrorHandlers} from '~/util/devErrorReporter'

export default defineNuxtPlugin(async (nuxtApp) => {
    initTokenRefresh()

    const themeReady = useTheme().initFromLocalStorage()
    const timeout = new Promise<void>(resolve => setTimeout(resolve, 1000))
    await Promise.race([themeReady, timeout])

    const devErrorHandler = installDevErrorHandlers()
    if (devErrorHandler) {
        nuxtApp.vueApp.config.errorHandler = devErrorHandler
    }
})
