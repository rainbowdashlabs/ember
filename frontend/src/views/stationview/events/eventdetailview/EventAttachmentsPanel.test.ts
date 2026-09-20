/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import EventAttachmentsPanel from './EventAttachmentsPanel.vue'
import FilePreviewModal from '@/components/documents/FilePreviewModal.vue'
import type {EventAttachment} from '@/api/events'

const listEventAttachments = vi.fn()
const downloadAuthed = vi.fn()

vi.mock('@/api', () => ({
    events: {
        listEventAttachments: (...args: unknown[]) => listEventAttachments(...args),
        eventAttachmentUrl: (eventId: number, attachmentId: number) =>
            `/events/${eventId}/attachments/${attachmentId}/file`,
        eventAttachmentPictureUrl: (eventId: number, attachmentId: number, width?: number) =>
            `/events/${eventId}/attachments/${attachmentId}/picture${width ? `?w=${width}` : ''}`,
    },
}))

vi.mock('@/api/client', () => ({
    default: {get: () => Promise.reject(new Error('no picture in a test'))},
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
     * Knowing which of four sheets is the map meant saving all four. The row opens the file where it
     * is, and saving it stays a thing of its own, so pressing the one never means the other.
     */
    it('opens the file for reading, and saves it only when asked to', async () => {
        listEventAttachments.mockResolvedValue([attachment({id: 5, label: 'Einsatzplan'})])

        const view = await settled(panel())
        expect(view.findComponent(FilePreviewModal).exists()).toBe(false)

        await view.find('[data-testid="event-attachment-open"]').trigger('click')

        const preview = view.findComponent(FilePreviewModal)
        expect(preview.exists()).toBe(true)
        expect(preview.props('source')).toBe('/events/12/attachments/5/file')
        expect(downloadAuthed).not.toHaveBeenCalled()
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

    /**
     * A question that could not be asked is not an answer of "nothing". Told apart, because an
     * event that hands over a form and a panel that failed to ask look the same otherwise.
     */
    it('says the files could not be asked for rather than that there are none', async () => {
        listEventAttachments.mockRejectedValue(new Error('nope'))

        const view = await settled(panel())

        expect(view.find('[data-testid="event-attachment-list"]').exists()).toBe(false)
        expect(view.find('[data-testid="event-attachment-failure"]').exists()).toBe(true)
    })

    it('says nothing at all where the event hands nothing over', async () => {
        const view = await settled(panel())

        expect(view.find('[data-testid="event-attachment-failure"]').exists()).toBe(false)
    })
})
