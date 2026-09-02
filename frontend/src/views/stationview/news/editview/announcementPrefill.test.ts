/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {buildAnnouncementDraft} from './announcementPrefill'
import type {EventField, StationEvent} from '@/api/events'
import type {RestrictionSelection} from '@/components/input/restriction'

const WORDS = {when: 'Wann:', until: 'bis', linkLabel: 'Zum Termin', yes: 'Ja', no: 'Nein'}

const EVENT: StationEvent = {
    id: 7,
    stationId: 'abc',
    name: 'Übungsdienst',
    startTime: '2026-09-01T19:00:00',
    endTime: '2026-09-01T21:00:00',
}

function field(over: Partial<EventField>): EventField {
    return {id: 1, eventId: 7, position: 0, overview: true, ...over}
}

function audience(over: Partial<RestrictionSelection> = {}): RestrictionSelection {
    return {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND', ...over}
}

describe('buildAnnouncementDraft', () => {
    it('carries the name, the one evening and the overview fields', () => {
        const draft = buildAnnouncementDraft(
            EVENT,
            '2026-09-08',
            [
                field({id: 1, name: 'Treffpunkt', fieldType: 'STRING', value: 'Gerätehaus', isPublic: false}),
                field({id: 2, name: 'Ort', fieldType: 'STRING', value: 'Marktplatz', isPublic: true}),
                field({id: 3, name: 'Intern', fieldType: 'STRING', value: 'nicht sichtbar', overview: false}),
            ],
            audience(),
            new Map(),
            WORDS,
            '/station/events/7/2026-09-08',
        )

        expect(draft.title).toBe('Übungsdienst')
        expect(draft.dateLabel).toContain('08.09.2026')
        expect(draft.dateLabel).toContain('19:00 bis 21:00')
        expect(draft.markdown).toContain('**Treffpunkt:** Gerätehaus')
        expect(draft.markdown).toContain('**Ort:** Marktplatz')
        expect(draft.markdown).not.toContain('nicht sichtbar')
        expect(draft.fields.map(f => f.name)).toEqual(['Treffpunkt', 'Ort'])
    })

    it('writes a stored value the way a reader reads it', () => {
        const draft = buildAnnouncementDraft(
            EVENT,
            '2026-09-08',
            [
                field({id: 1, name: 'Mit Fahrzeug', fieldType: 'BOOLEAN', value: 'true'}),
                field({id: 2, name: 'Leitung', fieldType: 'MEMBER', value: '42'}),
            ],
            audience(),
            new Map([[42, 'Anna Berger']]),
            WORDS,
            null,
        )

        expect(draft.markdown).toContain('**Mit Fahrzeug:** Ja')
        expect(draft.markdown).toContain('**Leitung:** Anna Berger')
        expect(draft.markdown).not.toContain('42')
    })

    it('starts an entry about a restricted appointment with the same audience', () => {
        const draft = buildAnnouncementDraft(
            EVENT,
            '2026-09-08',
            [],
            audience({groupIds: [3], memberIds: [42]}),
            new Map(),
            WORDS,
            '/station/events/7/2026-09-08',
        )

        expect(draft.restricted).toBe(true)
        expect(draft.audience.groupIds).toEqual([3])
        expect(draft.audience.memberIds).toEqual([42])
    })

    it('links back only where every reader of the entry has that address', () => {
        const restricted = buildAnnouncementDraft(
            EVENT, '2026-09-08', [], audience({groupIds: [3]}), new Map(), WORDS, '/station/events/7/2026-09-08')
        expect(restricted.linked).toBe(true)
        expect(restricted.markdown).toContain('[Zum Termin](/station/events/7/2026-09-08)')

        const open = buildAnnouncementDraft(
            EVENT, '2026-09-08', [], audience(), new Map(), WORDS, '/station/events/7/2026-09-08')
        expect(open.linked).toBe(false)
        expect(open.markdown).not.toContain('Zum Termin')
    })

    it('says nothing about a date where the appointment has none', () => {
        const draft = buildAnnouncementDraft(
            {id: 7, stationId: 'abc', name: 'Übungsdienst'}, null, [], audience(), new Map(), WORDS, null)

        expect(draft.dateLabel).toBe('')
        expect(draft.markdown).toBe('')
    })
})
