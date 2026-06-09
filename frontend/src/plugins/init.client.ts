/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {initTokenRefresh} from '~/api/client'
import {useTheme} from '~/composables/useTheme'
import {installDevErrorHandlers} from '~/util/devErrorReporter'

export default defineNuxtPlugin((nuxtApp) => {
    initTokenRefresh()
    useTheme().initFromLocalStorage()

    const devErrorHandler = installDevErrorHandlers()
    if (devErrorHandler) {
        nuxtApp.vueApp.config.errorHandler = devErrorHandler
    }
})
