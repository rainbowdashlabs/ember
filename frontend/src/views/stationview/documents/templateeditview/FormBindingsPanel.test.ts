/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {FormFieldKind, type FormField} from '@/api/generated/schema'
import FormBindingsPanel from './FormBindingsPanel.vue'

const NAMED: FormField = {
    name: 'Text1', kind: FormFieldKind.TEXT, rect: {page: 2, x: 10, y: 20, width: 100, height: 12},
    tooltip: 'Name des Kindes', value: 'Max Mustermann',
}
const BARE: FormField = {name: 'Text2', kind: FormFieldKind.TEXT, rect: null, tooltip: null, value: null}

async function mountPanel(chosen: string | null = null) {
    return mountSuspended(FormBindingsPanel, {
        props: {modelValue: [], formFields: [NAMED, BARE], placeholders: [], legal: false, chosen},
        global: {stubs: {PlaceholderTextInput: {template: '<input data-testid="binding-input"/>'}}},
    })
}

/**
 * A PDF often calls its fields Text1 and so on, so each entry says what the author called it, where it
 * sits and what it holds, and the entries and the fields on the page choose each other.
 */
describe('FormBindingsPanel', () => {
    it('names a field by its tooltip, its page and what it holds', async () => {
        const panel = await mountPanel()
        const [named, bare] = panel.findAll('[data-testid="form-binding"]')

        expect(named!.text()).toContain('Name des Kindes (Text1)')
        expect(named!.find('[data-testid="form-binding-where"]').text()).toBe('Seite 2 · im PDF: „Max Mustermann“')
        expect(bare!.text()).toContain('Text2')
        expect(bare!.find('[data-testid="form-binding-where"]').exists()).toBe(false)
    })

    it('marks the entry of the field chosen on the page', async () => {
        const panel = await mountPanel('Text2')
        const [named, bare] = panel.findAll('[data-testid="form-binding"]')

        expect(bare!.classes()).toContain('ring-2')
        expect(named!.classes()).not.toContain('ring-2')
    })

    it('chooses the field of the entry worked in', async () => {
        const panel = await mountPanel()

        await panel.findAll('[data-testid="binding-input"]')[1]!.trigger('focusin')

        expect(panel.emitted('choose')).toEqual([['Text2']])
    })
})
