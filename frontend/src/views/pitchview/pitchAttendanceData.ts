/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AttendanceEntry} from '@/api/attendance'
import type {MemberGroup, StationMember} from '@/api/types'
import type {PitchAttendance, PitchCheckMode} from './pitchTypes'

/**
 * The attendance a demonstration shows. The application's own list, summary and check panel read
 * these objects, so the preview cannot drift away from what the screens really do.
 */
function member(id: number, name: string): StationMember {
    return {id, stationId: 'wache', accountId: id, name, identity: {memberUid: `m-${id}`, name}}
}

const CREW: MemberGroup = {id: 1, stationId: 'wache', name: 'Löschgruppe', position: 0}
const RECRUITS: MemberGroup = {id: 2, stationId: 'wache', name: 'Anwärter', position: 1}

const ANNA = member(1, 'Anna Müller')
const BEN = member(2, 'Ben Krüger')
const CLARA = member(3, 'Clara Weiß')
const JONAS = member(4, 'Jonas Behr')
const MIRA = member(5, 'Mira Sand')

const MEMBERS = [ANNA, BEN, CLARA, JONAS, MIRA, member(6, 'Timo Reich')]

const SECTIONS = [
    {group: CREW, members: [ANNA, BEN, CLARA]},
    {group: RECRUITS, members: [JONAS, MIRA]},
]

/** The times are read back as timestamps, so they are given as one - on the day of the session. */
function at(time: string): string {
    const date = new Date()
    const [hours, minutes] = time.split(':')
    date.setHours(Number(hours), Number(minutes), 0, 0)
    return date.toISOString()
}

function entry(id: number, memberId: number, status: AttendanceEntry['status'],
               times?: {checkIn: string; checkOut: string}): AttendanceEntry {
    return {id, sessionId: 1, memberId, status, source: 'EXPECTED', ...times}
}

const ENTRIES: AttendanceEntry[] = [
    entry(1, 1, 'PRESENT', {checkIn: at('18:00'), checkOut: at('19:30')}),
    entry(2, 2, 'ABSENT'),
    entry(3, 3, 'DECLINED'),
    entry(4, 4, 'PRESENT', {checkIn: at('18:10'), checkOut: at('19:30')}),
    entry(5, 5, 'UNCONFIRMED'),
]

export const ATTENDANCE: PitchAttendance = {entries: ENTRIES, members: MEMBERS, sections: SECTIONS}

/** The entry the check is standing on, and how far it has come. */
export const ATTENDANCE_CHECK: PitchCheckMode = {
    row: {memberId: 5, entryId: 5},
    index: 0,
    total: 2,
    name: 'Mira Sand',
    identity: MIRA.identity ?? null,
}
