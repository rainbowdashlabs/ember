/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import DeleteFieldModal from './DeleteFieldModal.vue'

async function dialog(dependents: string[]) {
    return mountSuspended(DeleteFieldModal, {
        props: {modelValue: true, name: 'JuLeiCa', dependents},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
}

/**
 * A question others are shown under is not refused on deleting: the dialog names them and its button
 * removes their condition along with it.
 */
describe('DeleteFieldModal', () => {
    it('names the questions shown under it and offers to remove their condition', async () => {
        const modal = await dialog(['JuLeiCa bis', 'JuLeiCa Nummer'])

        expect(modal.find('[data-testid="field-dependents"]').text()).toContain('JuLeiCa bis')
        expect(modal.find('[data-testid="field-dependents"]').text()).toContain('JuLeiCa Nummer')
        expect(modal.find('[data-confirm]').text()).toBe('Bedingungen entfernen und löschen')
    })

    it('asks the plain question for a question nothing depends on', async () => {
        const modal = await dialog([])

        expect(modal.find('[data-testid="field-dependents"]').exists()).toBe(false)
        expect(modal.find('[data-confirm]').text()).toBe('Löschen')
    })
})
