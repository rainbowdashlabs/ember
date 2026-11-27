/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import type {EventAttachment, StationEvent} from '@/api/generated/schema'
import {copySourceOf, useEventCopy, type CopyTargets} from './useEventCopy'

const api = vi.hoisted(() => ({
    getEvent: vi.fn(),
    getRestrictions: vi.fn(),
    getEventFields: vi.fn(),
    getEventReminders: vi.fn(),
    getNextDate: vi.fn(),
    listEventAttachments: vi.fn(),
}))

vi.mock('@/api', () => ({events: api}))

/** A file of the original, its own unless a test marks it as from the event group. */
function file(id: number, fromEventGroup = false): EventAttachment {
    return {fileId: id, id, fromEventGroup} as EventAttachment
}

/** Every part of the editor a copy fills, each only recording what it was asked. */
function targets(): CopyTargets {
    return {
        form: {applyCopy: vi.fn()},
        fieldDefaults: {load: vi.fn(), keepOnly: vi.fn(), forgetValuesOf: vi.fn()},
        federationShare: {load: vi.fn()},
        documentRequirements: {takeFrom: vi.fn()},
        loadRegistrationFields: vi.fn(),
        sheetFieldIds: () => [4, 5],
        memberFieldIds: () => [5],
    } as unknown as CopyTargets
}

describe('copySourceOf', () => {
    it('reads the appointment the address names', () => {
        expect(copySourceOf('17')).toBe(17)
    })

    it('reads nothing that could not be an appointment', () => {
        expect(copySourceOf(undefined)).toBeNull()
        expect(copySourceOf('abc')).toBeNull()
        expect(copySourceOf('-3')).toBeNull()
        expect(copySourceOf(['17'])).toBeNull()
    })
})

describe('useEventCopy', () => {
    it('asks the server for the next date of a series and hands it to the form', async () => {
        api.getEvent.mockResolvedValue({id: 18, name: 'Übungsabend', eventType: 'RECURRING'} as StationEvent)
        api.getRestrictions.mockResolvedValue({register: {}, view: {}})
        api.getEventFields.mockResolvedValue([])
        api.getEventReminders.mockResolvedValue([])
        api.listEventAttachments.mockResolvedValue([])
        api.getNextDate.mockResolvedValue('2026-11-24')
        const parts = targets()

        await useEventCopy(parts).copy(18)

        expect(api.getNextDate).toHaveBeenCalledWith(18)
        expect(parts.form.applyCopy).toHaveBeenCalledWith(expect.anything(), expect.any(String), '2026-11-24')
    })

    it('fills every part of the editor from the original and names it', async () => {
        api.getNextDate.mockClear()
        api.getEvent.mockResolvedValue({id: 17, name: 'Dienstabend', eventType: 'ONE_TIME'} as StationEvent)
        api.getRestrictions.mockResolvedValue({register: {}, view: {}})
        api.getEventFields.mockResolvedValue([])
        api.getEventReminders.mockResolvedValue([3])
        api.listEventAttachments.mockResolvedValue([file(1), file(2, true)])
        const parts = targets()
        const copying = useEventCopy(parts)

        const files = await copying.copy(17)

        expect(parts.form.applyCopy).toHaveBeenCalledWith(
            expect.objectContaining({reminders: [3]}), expect.any(String), null)
        expect(api.getNextDate).not.toHaveBeenCalled()
        expect(parts.loadRegistrationFields).toHaveBeenCalledWith(17)
        expect(parts.documentRequirements.takeFrom).toHaveBeenCalledWith({kind: 'events', id: 17})
        expect(parts.fieldDefaults.keepOnly).toHaveBeenCalledWith([4, 5])
        expect(parts.fieldDefaults.forgetValuesOf).toHaveBeenCalledWith([5])
        expect(files.map(entry => entry.fileId)).toEqual([1])
        expect(copying.copiedFrom.value).toEqual({id: 17, name: 'Dienstabend'})
    })
})
