/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createI18n} from 'vue-i18n'
import deDE from './de-DE'

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    fallbackLocale: 'de-DE',
    messages: {
        'de-DE': deDE,
    },
})

export default i18n
