/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {defineComponent, h} from 'vue'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import DividerEditor from './DividerEditor.vue'
import DividerCell from '../cells/DividerCell.vue'
import type ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import de from '@/i18n/de-DE'
import {provideBlockEditorOptions} from '@/composables/useBlockEditorOptions'

/**
 * A divider runs vertically only where the editor offers it, which is a letter, and a vertical one has
 * no label to set and draws an upright line.
 */
describe('DividerEditor', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de, en: {}}})

    function editor(config: Record<string, unknown>, verticalDivider: boolean) {
        const updates: Record<string, unknown>[] = []
        const Host = defineComponent({
            setup() {
                provideBlockEditorOptions({verticalDivider})
                return () => h(DividerEditor, {config, 'onUpdate:config': (next: Record<string, unknown>) => updates.push(next)})
            },
        })
        return {view: mount(Host, {global: {plugins: [i18n]}}), updates}
    }

    it('offers no vertical line on a page', () => {
        expect(editor({}, false).view.find('[data-testid="divider-vertical"]').exists()).toBe(false)
    })

    it('turns the line upright in a letter', async () => {
        const {view, updates} = editor({label: 'Termine'}, true)

        await view.getComponent<typeof ToggleInput>('[data-testid="divider-vertical"]').vm.$emit('update:modelValue', true)

        expect(updates.at(-1)).toEqual({label: 'Termine', vertical: true})
    })

    it('hides the label of a vertical line', () => {
        const text = editor({vertical: true}, true).view.text()

        expect(text).not.toContain(de.stationPages.editor.dividerLabel)
    })

    it('draws an upright line for a vertical divider', () => {
        const view = mount(DividerCell, {props: {config: {vertical: true}}})

        expect(view.find('[data-testid="divider-vertical-line"]').exists()).toBe(true)
    })
})
