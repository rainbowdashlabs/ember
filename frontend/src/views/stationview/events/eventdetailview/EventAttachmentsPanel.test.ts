/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import EventAttachmentsPanel from './EventAttachmentsPanel.vue'
import type {EventAttachment} from '@/api/events'

const listEventAttachments = vi.fn()
const downloadAuthed = vi.fn()

vi.mock('@/api', () => ({
    events: {
        listEventAttachments: (...args: unknown[]) => listEventAttachments(...args),
        eventAttachmentUrl: (eventId: number, attachmentId: number) =>
            `/events/${eventId}/attachments/${attachmentId}/file`,
    },
}))

vi.mock('@/util/downloadAuthed', () => ({
    downloadAuthed: (...args: unknown[]) => downloadAuthed(...args),
}))

function attachment(overrides: Partial<EventAttachment> = {}): EventAttachment {
    return {
        id: 1,
        eventId: 12,
        fileId: 40,
        label: null,
        internal: false,
        sortOrder: 0,
        createdAt: '2026-05-12T10:00:00Z',
        fileName: 'laufzettel.pdf',
        mimeType: 'application/pdf',
        fileSize: 2048,
        contentHash: 'abc',
        ...overrides,
    }
}

/**
 * What the panel draws is what the server handed it.
 *
 * <p>Which files a reader may have is decided once, at the server. A panel that filtered again
 * would be a second opinion about an answered question, so the only thing it decides is how a file
 * is named and whether it is marked as kept back from the room.
 */
describe('EventAttachmentsPanel', () => {
    function panel() {
        return mount(EventAttachmentsPanel, {props: {eventId: 12}})
    }

    async function settled(view: ReturnType<typeof panel>) {
        await Promise.resolve()
        await Promise.resolve()
        await view.vm.$nextTick()
        return view
    }

    beforeEach(() => {
        vi.clearAllMocks()
        listEventAttachments.mockResolvedValue([])
    })

    it('says nothing where an event hands nothing over', async () => {
        const view = await settled(panel())

        expect(view.find('[data-testid="event-attachment-list"]').exists()).toBe(false)
    })

    it('lists what the reader was handed, and marks what is kept back from the room', async () => {
        listEventAttachments.mockResolvedValue([
            attachment(),
            attachment({id: 2, internal: true, label: 'Einsatzplan', fileName: 'plan.pdf'}),
        ])

        const view = await settled(panel())
        const rows = view.findAll('[data-testid="event-attachment-row"]')

        expect(rows).toHaveLength(2)
        expect(rows[0]!.find('[data-testid="event-attachment-internal-badge"]').exists()).toBe(false)
        expect(rows[1]!.find('[data-testid="event-attachment-internal-badge"]').exists()).toBe(true)
    })

    /** A label is what the file is called where somebody wrote one; the file name where nobody did. */
    it('calls a file by its label, and by its file name where there is none', async () => {
        listEventAttachments.mockResolvedValue([
            attachment({label: '   '}),
            attachment({id: 2, label: 'Einsatzplan', fileName: 'plan.pdf'}),
        ])

        const view = await settled(panel())
        const rows = view.findAll('[data-testid="event-attachment-row"]')

        expect(rows[0]!.text()).toContain('laufzettel.pdf')
        expect(rows[1]!.text()).toContain('Einsatzplan')
        expect(rows[1]!.text()).not.toContain('plan.pdf')
    })

    /**
     * The file travels with the reader's session, so it is fetched rather than linked to, and it is
     * saved under its own name: the label is what the page shows, the file name what carries the type.
     */
    it('fetches the file under the reader\'s own session and saves it by its file name', async () => {
        listEventAttachments.mockResolvedValue([attachment({id: 5, label: 'Einsatzplan'})])

        const view = await settled(panel())
        await view.find('[data-testid="event-attachment-download"]').trigger('click')

        expect(downloadAuthed).toHaveBeenCalledWith('/events/12/attachments/5/file', 'laufzettel.pdf')
    })

    it('stays empty where the files could not be asked for', async () => {
        listEventAttachments.mockRejectedValue(new Error('nope'))

        const view = await settled(panel())

        expect(view.find('[data-testid="event-attachment-list"]').exists()).toBe(false)
    })
})
