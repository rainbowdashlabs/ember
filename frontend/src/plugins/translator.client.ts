/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {setTranslator} from '@/util/translatorState'

/** Lends the browser app's i18n instance to the code that runs outside every component. */
export default defineNuxtPlugin({
    name: 'ember:translator',
    dependsOn: ['i18n:plugin'],
    setup(nuxtApp) {
        const i18n = nuxtApp.$i18n
        setTranslator(key => i18n.t(key))
    },
})
