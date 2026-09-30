/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {buildAnnouncementDraft, type AnnouncedEvent} from './announcementPrefill'
import type {EventField, StationEvent} from '@/api/events'
import type {RestrictionSelection} from '@/components/input/restriction'

const WORDS = {
    until: 'bis',
    yes: 'Ja',
    no: 'Nein',
    say: (sentence: string, values?: Record<string, string | number>) => `[${sentence}${values ? ` ${JSON.stringify(values)}` : ''}]`,
}

const EVENT: StationEvent = {
    id: 7,
    stationId: 'abc',
    name: 'Übungsdienst',
    eventType: 'RECURRING',
    description: 'Wir üben Knoten.',
    startTime: '2026-09-01T19:00:00',
    endTime: '2026-09-01T21:00:00',
}

function field(over: Partial<EventField>): EventField {
    return {id: 1, eventId: 7, position: 0, overview: true, ...over}
}

function audience(over: Partial<RestrictionSelection> = {}): RestrictionSelection {
    return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND', ...over}
}

function announced(over: Partial<AnnouncedEvent> = {}): AnnouncedEvent {
    return {event: EVENT, eventUid: 'uid-7', date: '2026-09-08', fields: [], ...over}
}

function texts(draft: ReturnType<typeof buildAnnouncementDraft>): string[] {
    return draft.rows.map(row => row.cells[0]?.content ?? '')
}

describe('buildAnnouncementDraft', () => {
    it('opens with the event block for the one occurrence, then the description, then the details', () => {
        const draft = buildAnnouncementDraft(
            announced({
                event: {...EVENT, requiresRegistration: true, registrationLimit: 12},
                fields: [
                    field({id: 1, name: 'Treffpunkt', fieldType: 'STRING', value: 'Gerätehaus', isPublic: false}),
                    field({id: 3, name: 'Intern', fieldType: 'STRING', value: 'nicht sichtbar', overview: false}),
                ],
            }),
            audience(),
            new Map(),
            WORDS,
        )

        expect(draft.title).toBe('Übungsdienst')
        expect(draft.rows.map(row => row.cells[0]?.contentType)).toEqual(['FEATURED_EVENT', 'MARKDOWN', 'MARKDOWN'])
        expect(draft.rows[0]?.cells[0]?.config).toEqual({eventUid: 'uid-7', date: '2026-09-08'})
        expect(draft.rows.map(row => row.sortOrder)).toEqual([0, 1, 2])
        expect(texts(draft)[1]).toBe('Wir üben Knoten.')
        expect(texts(draft)[2]).toContain('[registrationLimit {"count":12}]')
        expect(texts(draft)[2]).toContain('**Treffpunkt:** Gerätehaus')
        expect(texts(draft)[2]).not.toContain('nicht sichtbar')
        expect(draft.embedded).toBe(true)
        expect(draft.dateLabel).toContain('08.09.2026')
        expect(draft.dateLabel).toContain('19:00 bis 21:00')
    })

    it('leaves a one-off appointment on its own start rather than one of its days', () => {
        const draft = buildAnnouncementDraft(
            announced({event: {...EVENT, eventType: 'ONE_TIME'}}), audience(), new Map(), WORDS)

        expect(draft.rows[0]?.cells[0]?.config).toEqual({eventUid: 'uid-7', date: null})
        expect(draft.dateLabel).toContain('08.09.2026')
    })

    it('writes a stored value the way a reader reads it', () => {
        const draft = buildAnnouncementDraft(
            announced({
                fields: [
                    field({id: 1, name: 'Mit Fahrzeug', fieldType: 'BOOLEAN', value: 'true'}),
                    field({id: 2, name: 'Leitung', fieldType: 'MEMBER', value: '42'}),
                ],
            }),
            audience(),
            new Map([[42, 'Anna Berger']]),
            WORDS,
        )

        const details = texts(draft).at(-1) ?? ''
        expect(details).toContain('**Mit Fahrzeug:** Ja')
        expect(details).toContain('**Leitung:** Anna Berger')
        expect(details).not.toContain('42')
    })

    it('starts an entry about a restricted appointment with the same audience', () => {
        const draft = buildAnnouncementDraft(announced(), audience({groupIds: [3], memberIds: [42]}), new Map(), WORDS)

        expect(draft.restricted).toBe(true)
        expect(draft.audience.groupIds).toEqual([3])
        expect(draft.audience.memberIds).toEqual([42])
    })

    it('leaves out the blocks it has nothing for', () => {
        const draft = buildAnnouncementDraft(
            announced({event: {id: 7, stationId: 'abc', name: 'Übungsdienst'}, eventUid: null, date: null}),
            audience(),
            new Map(),
            WORDS,
        )

        expect(draft.rows).toEqual([])
        expect(draft.embedded).toBe(false)
        expect(draft.dateLabel).toBe('')
    })
})
