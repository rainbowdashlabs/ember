/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises} from '@vue/test-utils'
import {EditScopeKind, EventChangePart, EventType, type EventRequest} from '@/api/generated/schema'
import {useSaveChoice} from './useSaveChoice'

const previewChange = vi.fn()

vi.mock('@/api', () => ({
    events: {previewChange: (...args: unknown[]) => previewChange(...args)},
}))

const draft: EventRequest = {
    name: 'Dienst',
    eventType: EventType.RECURRING,
    startTime: '2099-01-06T17:00:00Z',
    endTime: '2099-01-06T19:00:00Z',
}

/** What the save dialog holds, and what it asks the server while the editor chooses. */
describe('useSaveChoice', () => {
    beforeEach(() => {
        previewChange.mockReset()
        previewChange.mockResolvedValue({noticeAudience: 4, startMoves: true, newStart: '19:00', moveAudience: 2, everybody: 4})
    })

    it('opens on every date, telling nobody, and counts for that choice', async () => {
        const choice = useSaveChoice(() => 9, () => draft, () => [EventChangePart.DESCRIPTION], key => key)

        choice.start('2099-01-06')
        await flushPromises()

        expect(choice.kind.value).toBe(EditScopeKind.ALL)
        expect(choice.date.value).toBe('2099-01-06')
        expect(previewChange).toHaveBeenCalledWith(9, {...draft, scope: {kind: EditScopeKind.ALL}})
        expect(choice.preview.value?.noticeAudience).toBe(4)
        expect(choice.choice.value.notice.tell).toBe(false)
    })

    it('asks again for one date and waits until it has one', async () => {
        const choice = useSaveChoice(() => 9, () => draft, () => [], key => key)
        choice.start('2000-01-01')
        await flushPromises()
        expect(choice.date.value).toBe('')

        choice.kind.value = EditScopeKind.THIS
        await flushPromises()
        expect(choice.preview.value).toBeNull()

        choice.date.value = '2099-01-13'
        await flushPromises()
        expect(previewChange).toHaveBeenLastCalledWith(9, {
            ...draft,
            scope: {kind: EditScopeKind.THIS, date: '2099-01-13'},
        })
    })

    it('tells only where there is something to tell and trims the note', async () => {
        previewChange.mockResolvedValue({noticeAudience: 4, startMoves: false, newStart: null, moveAudience: 0, everybody: 4})
        const choice = useSaveChoice(() => 9, () => draft, () => [], key => key)
        choice.start(null)
        await flushPromises()
        choice.tell.value = true
        choice.note.value = '  Helm  '

        expect(choice.canTell.value).toBe(false)
        expect(choice.choice.value.notice).toEqual({tell: false, changedParts: [], note: 'Helm'})
    })
    it('keeps why the server would not count a choice', async () => {
        previewChange.mockRejectedValue(new Error('refused'))
        const choice = useSaveChoice(() => 9, () => draft, () => [], key => key)

        choice.start(null)
        await flushPromises()

        expect(choice.preview.value).toBeNull()
        expect(choice.previewFailure.value).not.toBeNull()
    })
})
