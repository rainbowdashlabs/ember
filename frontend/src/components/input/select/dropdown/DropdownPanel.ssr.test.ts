/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment node */
import {describe, expect, it} from 'vitest'
import {createSSRApp, h, type Component} from 'vue'
import {renderToString} from 'vue/server-renderer'
import {createI18n} from 'vue-i18n'
import deDE from '@/i18n/de-DE'
import MultiSelectDropdown from '../MultiSelectDropdown.vue'
import SingleSelectDropdown from '../SingleSelectDropdown.vue'
import LinkSearchInput from '@/components/input/text/LinkSearchInput.vue'

const OPTIONS = [{value: 'a', label: 'Apfel'}]

/**
 * The cluster pages are rendered on the server and hold dropdowns, so a closed dropdown has to
 * render there without reaching for a document that is not there.
 */
describe('Dropdowns on the server', () => {
    it.each<[string, Component, Record<string, unknown>]>([
        ['SingleSelectDropdown', SingleSelectDropdown, {modelValue: 'a', options: OPTIONS}],
        ['MultiSelectDropdown', MultiSelectDropdown, {modelValue: ['a'], options: OPTIONS}],
        ['LinkSearchInput', LinkSearchInput, {modelValue: 'https://example.org', noFiles: true}],
    ])('renders %s closed without a document', async (_name, component, props) => {
        const app = createSSRApp({render: () => h(component, props)})
        app.use(createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': deDE, en: {}}}))
        app.component('font-awesome-icon', {render: () => h('span')})

        const html = await renderToString(app)

        expect(html).not.toContain('role="listbox"')
        expect(html.length).toBeGreaterThan(0)
    })
})
