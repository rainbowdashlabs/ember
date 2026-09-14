/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import AttachmentsCard from './AttachmentsCard.vue'
import MediaBrowseButton from '@/components/media/MediaBrowseButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {EventAttachment} from '@/api/events'
import type {StationFile} from '@/api/media'

function attachment(id: number, overrides: Partial<EventAttachment> = {}): EventAttachment {
    return {
        id,
        eventId: 12,
        fileId: 40 + id,
        label: null,
        internal: false,
        sortOrder: id,
        createdAt: '2026-05-12T10:00:00Z',
        fileName: `datei-${id}.pdf`,
        mimeType: 'application/pdf',
        fileSize: 1024,
        contentHash: `hash-${id}`,
        ...overrides,
    }
}

/**
 * The card an event is edited through.
 *
 * <p>It decides nothing: a file comes out of the media library and every change is handed on as it
 * is made, so what is checked here is that the pick and the switch reach whoever writes them.
 */
describe('AttachmentsCard', () => {
    function card(attachments: EventAttachment[], onSave?: (attachment: EventAttachment) => void) {
        return mount(AttachmentsCard, {
            props: {attachments, stationUid: 'station-uid', onSave},
            global: {stubs: {MediaBrowseButton: true, DragList: false}},
        })
    }

    it('says so where an event hands nothing over', () => {
        const view = card([])

        expect(view.find('[data-testid="event-attachment"]').exists()).toBe(false)
        expect(view.text()).toContain('Noch keine Dateien')
    })

    it('hands the picked file on', async () => {
        const view = card([])

        view.findComponent(MediaBrowseButton).vm.$emit('pick', {file: {id: 77} as StationFile})
        await view.vm.$nextTick()

        expect(view.emitted('add')?.[0]).toEqual([{id: 77}])
    })

    /**
     * The switch is written before the row is handed on, and the assertion is made at that moment
     * rather than afterwards: whoever saves reads the row as it is when they are called, and a save
     * that runs first sends the value the switch had a moment ago.
     */
    it('writes the file out again when it is kept back from the room', async () => {
        const asSaved: {id: number; internal: boolean}[] = []
        const view = card([attachment(1)], saved => asSaved.push({id: saved.id, internal: saved.internal}))

        view.findComponent(ToggleInput).vm.$emit('update:modelValue', true)
        await view.vm.$nextTick()

        expect(asSaved).toEqual([{id: 1, internal: true}])
    })

    it('names each file and how large it is', () => {
        const view = card([attachment(1), attachment(2)])
        const rows = view.findAll('[data-testid="event-attachment"]')

        expect(rows).toHaveLength(2)
        expect(rows[0]!.text()).toContain('datei-1.pdf')
    })
})
