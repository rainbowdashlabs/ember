/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {useEventAttachments} from './useEventAttachments'
import type {EventAttachment} from '@/api/events'
import type {StationFile} from '@/api/media'

const listEventAttachments = vi.fn()
const attachEventFile = vi.fn()
const updateEventAttachment = vi.fn()
const reorderEventAttachments = vi.fn()
const detachEventFile = vi.fn()

vi.mock('@/api', () => ({
    events: {
        listEventAttachments: (...args: unknown[]) => listEventAttachments(...args),
        attachEventFile: (...args: unknown[]) => attachEventFile(...args),
        updateEventAttachment: (...args: unknown[]) => updateEventAttachment(...args),
        reorderEventAttachments: (...args: unknown[]) => reorderEventAttachments(...args),
        detachEventFile: (...args: unknown[]) => detachEventFile(...args),
    },
}))

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

const pickedFile = {id: 77, fileName: 'laufzettel.pdf'} as StationFile

/**
 * The files of an event while somebody is editing it.
 *
 * <p>Nothing here waits for a save: the event already exists, and a file that only lands when the
 * form is submitted is a file somebody believes they attached. What is checked is that each change
 * reaches the server as it is made, and that an event that is not there yet is left alone.
 */
describe('useEventAttachments', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        listEventAttachments.mockResolvedValue([])
        attachEventFile.mockImplementation(async (_eventId: number, fileId: number) =>
            attachment(9, {fileId}))
        updateEventAttachment.mockResolvedValue(undefined)
        reorderEventAttachments.mockResolvedValue(undefined)
        detachEventFile.mockResolvedValue(undefined)
    })

    it('hangs a picked file on the event, open to the room until somebody says otherwise', async () => {
        const files = useEventAttachments(() => 12)

        await files.add(pickedFile)

        expect(attachEventFile).toHaveBeenCalledWith(12, 77, null, false)
        expect(files.attachments.value).toHaveLength(1)
    })

    it('writes the switch as it is flipped', async () => {
        const files = useEventAttachments(() => 12)
        listEventAttachments.mockResolvedValue([attachment(1)])
        await files.load()

        const first = files.attachments.value[0]!
        first.internal = true
        first.label = 'Einsatzplan'
        await files.save(first)

        expect(updateEventAttachment).toHaveBeenCalledWith(12, 1, 'Einsatzplan', true)
    })

    it('drops a detached file from the list it just left', async () => {
        const files = useEventAttachments(() => 12)
        listEventAttachments.mockResolvedValue([attachment(1), attachment(2)])
        await files.load()

        await files.remove(0)

        expect(detachEventFile).toHaveBeenCalledWith(12, 1)
        expect(files.attachments.value.map(a => a.id)).toEqual([2])
    })

    it('sends the order the editor dragged them into', async () => {
        const files = useEventAttachments(() => 12)
        listEventAttachments.mockResolvedValue([attachment(1), attachment(2), attachment(3)])
        await files.load()

        await files.reorder(2, 0)

        expect(files.attachments.value.map(a => a.id)).toEqual([3, 1, 2])
        expect(reorderEventAttachments).toHaveBeenCalledWith(12, [3, 1, 2])
    })

    /** An event that has not been created has nothing to hang a file on. */
    it('asks nothing of an event that is not there yet', async () => {
        const files = useEventAttachments(() => null)

        await files.load()
        await files.add(pickedFile)
        await files.remove(0)
        await files.reorder(0, 1)

        expect(listEventAttachments).not.toHaveBeenCalled()
        expect(attachEventFile).not.toHaveBeenCalled()
        expect(reorderEventAttachments).not.toHaveBeenCalled()
    })

    it('keeps the list it had where the files could not be asked for', async () => {
        const files = useEventAttachments(() => 12)
        listEventAttachments.mockRejectedValue(new Error('nope'))

        await files.load()

        expect(files.attachments.value).toEqual([])
        expect(files.error.value).toBe('load')
        expect(files.loading.value).toBe(false)
    })
})
