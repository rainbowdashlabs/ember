/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import GroupFormModal from './GroupFormModal.vue'

async function form(saveLabel?: string) {
    return mountSuspended(GroupFormModal, {
        props: {modelValue: true, name: 'Atemschutz', color: '', isEdit: true, saving: false, saveLabel},
        global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
    })
}

/** A save that does more than save says so on its button. */
describe('GroupFormModal', () => {
    it('says what the save does where it takes members out', async () => {
        const modal = await form('Speichern und aus der Gruppe nehmen (2)')
        const buttons = modal.findAll('button').map(button => button.text())

        expect(buttons).toContain('Speichern und aus der Gruppe nehmen (2)')
    })

    it('says save otherwise', async () => {
        const modal = await form()
        const buttons = modal.findAll('button').map(button => button.text())

        expect(buttons).toContain('Speichern')
    })
})
