/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AttendanceEntry, MemberWithName, SessionAudience} from '@/api/generated/schema'
import {StationUserType, StationUserTypeLabels, type MemberGroup} from '@/api/types'

/** One block of names on a sheet, headed by a group or a user type, or by nothing for the rest. */
export interface MemberSection {
    key: string
    /** What the block is headed with, null for everybody no group or type took. */
    title: string | null
    members: MemberWithName[]
}

/** What the blocks of a sheet are built from. */
export interface MemberSectionSources {
    /** Whom the sheet expects, its own or its template's. */
    audience: SessionAudience
    groups: MemberGroup[]
    groupMembers: Map<number, MemberWithName[]>
    allMembers: MemberWithName[]
    entries: AttendanceEntry[]
    locale: string
}

/**
 * The blocks a sheet is read in: a block per group of its audience, in the audience's order, with
 * every member of the group; then a block per user type for the members of that type no group took;
 * then everybody else who stands on the sheet.
 *
 * <p>A group and a type list their people whether or not they have an entry yet, since both are
 * whom the sheet expects. Somebody who has left the station is listed by type only where they have
 * an entry.
 */
export function buildMemberSections(sources: MemberSectionSources): MemberSection[] {
    const byName = (a: MemberWithName, b: MemberWithName) =>
        (a.name ?? '').localeCompare(b.name ?? '', sources.locale)
    const sections: MemberSection[] = []
    const assigned = new Set<number>()
    const take = (key: string, title: string | null, members: MemberWithName[]) => {
        if (members.length === 0) return
        const sorted = [...members].sort(byName)
        sections.push({key, title, members: sorted})
        sorted.forEach(member => assigned.add(member.id))
    }

    for (const groupId of sources.audience.groupIds) {
        const group = sources.groups.find(candidate => candidate.id === groupId)
        if (group) take(`group-${groupId}`, group.name ?? '', sources.groupMembers.get(groupId) ?? [])
    }

    const entered = new Set(sources.entries.map(entry => entry.memberId))
    for (const type of Object.values(StationUserType)) {
        if (!sources.audience.userTypes.includes(type)) continue
        take(`type-${type}`, StationUserTypeLabels[type], sources.allMembers.filter(member =>
            member.userType === type
            && !assigned.has(member.id)
            && (!member.formerAt || entered.has(member.id))))
    }

    take('others', null, sources.entries
        .filter(entry => !assigned.has(entry.memberId))
        .map(entry => sources.allMembers.find(member => member.id === entry.memberId))
        .filter((member): member is MemberWithName => member != null))
    return sections
}
