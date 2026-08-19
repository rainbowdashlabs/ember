/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    WaitingList, WaitingListEntry, WaitingListEntryWithScore, WaitingListField, WaitingListWithCount,
} from '@/api/waitingList'
import type {MemberGroup} from '@/api/types'
import type {PitchWaitlist} from './pitchTypes'

/**
 * The waiting list a demonstration walks along. Entries, scores and sections are handed to the
 * application's own sections, so the states and the ranking are the ones the screens really show.
 */
function daysAgo(days: number): string {
    const date = new Date()
    date.setDate(date.getDate() - days)
    return date.toISOString()
}

const FIELDS: WaitingListField[] = [
    {id: 1, listId: 1, name: 'Geburtsdatum', fieldType: 'DATE', config: {}, position: 0,
        required: true, isPublic: true},
    {id: 2, listId: 1, name: 'Wohnort', fieldType: 'TEXT', config: {}, position: 1,
        required: false, isPublic: true},
    {id: 3, listId: 1, name: 'Geschwister dabei', fieldType: 'BOOLEAN', config: {}, position: 2,
        required: false, isPublic: true},
]

const LIST: WaitingList = {
    id: 1, stationId: 'wache', name: 'Warteliste 2026',
    description: 'Anmeldungen für die Aufnahme im kommenden Jahr',
    scoringFormula: '[wartezeit_monate] + (age([Geburtsdatum]) < 12 ? 5 : 0)',
    confirmIntervalDays: 90, createdAt: daysAgo(300), visibleFields: [1, 2],
    testingGroupId: 3, joinGroupId: 2, attendanceThreshold: 4, isPublic: true,
}

export const WAITLISTS: WaitingListWithCount[] = [
    {list: LIST, entryCount: 34},
    {
        list: {...LIST, id: 2, name: 'Warteliste Erwachsene', isPublic: false,
            description: 'Quereinstieg in die aktive Abteilung', createdAt: daysAgo(120)},
        entryCount: 6,
    },
]

function entry(id: number, firstname: string, lastname: string,
               status: WaitingListEntry['status'], rest: Partial<WaitingListEntry> = {}): WaitingListEntry {
    return {
        id, listId: 1, firstname, lastname, parentName: '', email: `${firstname.toLowerCase()}@example.org`,
        accessToken: `token-${id}`, status, confirmedAt: daysAgo(20), createdAt: daysAgo(200),
        notes: '', attendanceCount: 0, ...rest,
    }
}

function withScore(base: WaitingListEntry, score: number,
                   values: {fieldId: number; value: unknown}[] = [],
                   guardians: {firstname: string; lastname: string; email: string; phone: string}[] = [],
): WaitingListEntryWithScore {
    return {
        entry: base, score,
        values: values.map(value => ({entryId: base.id, ...value})),
        guardians: guardians.map((guardian, index) => ({
            id: index + 1, entryId: base.id, position: index, ...guardian,
        })),
    }
}

const PENDING: WaitingListEntryWithScore[] = [
    withScore(
        entry(10, 'Lena', 'Sommer', 'PENDING', {createdAt: daysAgo(2), notes: 'Kennt Jonas aus der Schule'}),
        0,
        [{fieldId: 1, value: '2014-03-19'}, {fieldId: 2, value: 'Musterstadt'}],
        [{firstname: 'Petra', lastname: 'Sommer', email: 'p.sommer@example.org', phone: '0151 2345678'}],
    ),
]

const WAITING: WaitingListEntryWithScore[] = [
    withScore(entry(1, 'Nele', 'Brandt', 'WAITING', {createdAt: daysAgo(420)}), 38,
        [{fieldId: 1, value: '2015-06-02'}, {fieldId: 2, value: 'Musterstadt'}]),
    withScore(entry(2, 'Ole', 'Reinhardt', 'INVITED', {createdAt: daysAgo(360), invitedAt: daysAgo(6)}), 31,
        [{fieldId: 1, value: '2013-11-24'}, {fieldId: 2, value: 'Talbach'}]),
    withScore(entry(3, 'Emil', 'Hartwig', 'WAITING', {createdAt: daysAgo(240)}), 24,
        [{fieldId: 1, value: '2014-01-08'}, {fieldId: 2, value: 'Musterstadt'}]),
    withScore(entry(4, 'Frida', 'Lohse', 'WAITING', {createdAt: daysAgo(90)}), 13,
        [{fieldId: 1, value: '2012-09-30'}, {fieldId: 2, value: 'Bergheim'}]),
]

const TESTING: WaitingListEntryWithScore[] = [
    withScore(entry(5, 'Mats', 'Kellner', 'TESTING',
        {createdAt: daysAgo(300), testingAt: daysAgo(35), attendanceCount: 4}), 29),
    withScore(entry(6, 'Ida', 'Vogt', 'TESTING',
        {createdAt: daysAgo(280), testingAt: daysAgo(14), attendanceCount: 2}), 27),
]

const GROUPS: MemberGroup[] = [
    {id: 2, stationId: 'wache', name: 'Löschgruppe', position: 0},
    {id: 3, stationId: 'wache', name: 'Schnupperkinder', position: 1},
]

export const WAITLIST: PitchWaitlist = {
    lists: WAITLISTS,
    list: LIST,
    fields: FIELDS,
    groups: GROUPS,
    pending: PENDING,
    waiting: WAITING,
    testing: TESTING,
}
