/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import EventSaveDialog from './EventSaveDialog.vue'
import EventSaveScopeChoice from './EventSaveScopeChoice.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {EditScopeKind, type ChangePreview} from '@/api/generated/schema'

function preview(overrides: Partial<ChangePreview> = {}): ChangePreview {
    return {noticeAudience: 6, startMoves: false, newStart: null, moveAudience: 0, everybody: 6, ...overrides}
}

/**
 * The dialog that asks, on saving an appointment, which dates the change applies to and whether the
 * people it concerns are told.
 */
describe('EventSaveDialog', () => {
    function dialog(props: Partial<{
        series: boolean
        preview: ChangePreview | null
        canTell: boolean
        kind: EditScopeKind
        date: string
        tell: boolean
        note: string
        filesChanged: boolean
    }> = {}) {
        return mount(EventSaveDialog, {
            props: {
                modelValue: true,
                kind: EditScopeKind.ALL,
                date: '',
                tell: false,
                note: '',
                series: true,
                preview: preview(),
                canTell: true,
                saving: false,
                ...props,
            },
            global: {stubs: {Modal: {template: '<div><slot/></div>'}}},
        })
    }

    it('offers the dates only for a series', () => {
        expect(dialog().findComponent(EventSaveScopeChoice).exists()).toBe(true)
        expect(dialog({series: false}).findComponent(EventSaveScopeChoice).exists()).toBe(false)
    })

    it('counts who would be told and keeps quiet by default', () => {
        const view = dialog()

        expect(view.text()).toContain('Benachrichtigen (Personen: 6)')
        const quiet = view.find('input[value="quiet"]').element as HTMLInputElement
        expect(quiet.checked).toBe(true)
    })

    it('cannot tell about nothing', () => {
        const tell = dialog({canTell: false}).find('input[value="tell"]').element as HTMLInputElement

        expect(tell.disabled).toBe(true)
    })

    it('says that a moved start is told in any case', () => {
        const view = dialog({preview: preview({startMoves: true, newStart: '19:00', moveAudience: 3})})

        expect(view.text()).toContain('19:00')
        expect(view.text()).toContain('Personen: 3')
    })

    it('waits for the date one date needs, and for a note that fits', async () => {
        expect(dialog({kind: EditScopeKind.THIS}).findComponent(PrimaryButton).attributes('disabled') !== undefined).toBe(true)
        expect(dialog({kind: EditScopeKind.THIS, date: '2099-01-06'})
            .findComponent(PrimaryButton).attributes('disabled') !== undefined).toBe(false)
        expect(dialog({tell: true, note: 'x'.repeat(501)}).findComponent(PrimaryButton).attributes('disabled') !== undefined).toBe(true)

        const view = dialog()
        await view.findComponent(PrimaryButton).trigger('click')
        expect(view.emitted('save')).toHaveLength(1)
    })
    it('offers every date only once files were changed', () => {
        const view = dialog({filesChanged: true})

        expect(view.findComponent(EventSaveScopeChoice).exists()).toBe(false)
        expect(view.text()).toContain('Dateien')
    })
})
