/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import i18n from '~/i18n'

export default defineNuxtPlugin((nuxtApp) => {
    nuxtApp.vueApp.use(i18n)
})
