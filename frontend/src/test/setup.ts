/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {config} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import deDE from '@/i18n/de-DE'

/**
 * Everything every mounted component needs and no test should have to repeat.
 *
 * The i18n plugin is real rather than stubbed: the app has one locale, so a test asserting a label
 * asserts the label a user reads. The stubs are the two globals that would otherwise drag half the
 * app into a mount - the icon library and the router behind every link.
 */
const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    messages: {'de-DE': deDE},
})

config.global.plugins = [i18n]

config.global.stubs = {
    'font-awesome-icon': {
        template: '<span data-testid="icon" />',
        props: ['icon'],
    },
    NuxtLink: {
        template: '<a :href="to"><slot /></a>',
        props: ['to'],
    },
}
