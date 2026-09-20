/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {clearDraft, DRAFT_LIFETIME_MS, readDraft, saveDraft} from './pageDrafts'

const store = new Map<string, string>()

vi.mock('@/api/storage', () => ({
    getItem: (key: string) => store.get(key) ?? null,
    setItem: (key: string, value: string) => {
        store.set(key, value)
    },
}))

/**
 * What is kept of somebody's unsaved writing, and for how long.
 *
 * <p>A draft is a rescue rather than a second copy of the page, so it expires, it is capped, and it
 * never gets in the way: storage that refuses to answer must not stop anybody typing.
 */
describe('page drafts', () => {
    beforeEach(() => {
        store.clear()
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.restoreAllMocks()
    })

    it('gives back what was written', () => {
        saveDraft('page:1', {rows: ['a', 'b']})

        expect(readDraft<{rows: string[]}>('page:1')?.content).toEqual({rows: ['a', 'b']})
    })

    it('keeps several things apart', () => {
        saveDraft('page:1', 'eins')
        saveDraft('news:2', 'zwei')

        expect(readDraft<string>('page:1')?.content).toBe('eins')
        expect(readDraft<string>('news:2')?.content).toBe('zwei')
    })

    it('has nothing to say about something never written', () => {
        expect(readDraft('page:404')).toBeNull()
    })

    /** Nobody wants to be offered work they have long since forgotten writing. */
    it('forgets a draft once it is old', () => {
        saveDraft('page:1', 'alt')

        vi.advanceTimersByTime(DRAFT_LIFETIME_MS + 1000)

        expect(readDraft('page:1')).toBeNull()
    })

    it('keeps one that is not old yet', () => {
        saveDraft('page:1', 'frisch')

        vi.advanceTimersByTime(DRAFT_LIFETIME_MS - 1000)

        expect(readDraft<string>('page:1')?.content).toBe('frisch')
    })

    /** A browser gives the whole application a few megabytes, and a page of blocks is not small. */
    it('keeps only the newest handful', () => {
        for (let i = 0; i < 14; i++) {
            saveDraft(`page:${i}`, `inhalt ${i}`)
            vi.advanceTimersByTime(1000)
        }

        expect(readDraft('page:13')).not.toBeNull()
        expect(readDraft('page:0')).toBeNull()
    })

    it('forgets one when it is told to', () => {
        saveDraft('page:1', 'weg damit')

        clearDraft('page:1')

        expect(readDraft('page:1')).toBeNull()
    })

    it('says when it was written', () => {
        vi.setSystemTime(new Date('2026-09-20T10:00:00Z'))

        saveDraft('page:1', 'jetzt')

        expect(readDraft('page:1')?.savedAt).toBe(Date.parse('2026-09-20T10:00:00Z'))
    })

    /** Storage may be refused outright, and a rescue that breaks the writing is worse than none. */
    it('carries on where the stored value is nonsense', () => {
        store.set('page_drafts', 'not json at all')

        expect(readDraft('page:1')).toBeNull()
        expect(() => saveDraft('page:1', 'trotzdem')).not.toThrow()
        expect(readDraft<string>('page:1')?.content).toBe('trotzdem')
    })
})
