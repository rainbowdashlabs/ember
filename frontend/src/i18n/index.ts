/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {createI18n} from 'vue-i18n'
import deDE from './de-DE'
import en from './en'

/**
 * German is both the language shown and the language fallen back to.
 *
 * <p>The English file is deliberately partial, so every key it does not carry is read in German
 * rather than shown as its own name. That is what lets English grow a screen at a time instead of
 * waiting until all of it is translated.
 *
 * <p>Nothing switches the locale yet: a station carries the language its documents are written in,
 * and the interface will follow it once that reaches the browser. Until then this is the mechanism
 * without the switch, which is the half worth having first.
 */
const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    fallbackLocale: 'de-DE',
    messages: {
        'de-DE': deDE,
        en,
    },
})

export default i18n
