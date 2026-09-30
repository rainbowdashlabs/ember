/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberGroupSet} from '@/api/groupSets'
import {StationUserTypeLabels, type MemberGroup, type StationUserTypeName} from '@/api/types'
import {apiErrorBody, apiErrorCode, type ApiGroupConflict} from '@/util/apiError'

/** A change to groups refused for the members it would break a rule for, as the screen shows it. */
export interface GroupConflict {
    /** The code of the refusal, which says which rule it was. */
    code: string
    conflicts: ApiGroupConflict[]
}

/** The members a change to groups was refused for, where the refusal named any. */
export function groupConflictOf(e: unknown): GroupConflict | null {
    const conflicts = apiErrorBody(e)?.conflicts ?? []
    const code = apiErrorCode(e)
    return conflicts.length > 0 && code ? {code, conflicts} : null
}

/** The refusal of a binding that some members of the group do not fit, which can be answered by taking them out. */
export const BINDING_EXCLUDES_MEMBERS = 'M-128'

/** A set of groups with the groups that belong to it, in the order the station lists them. */
export interface SetOfGroups {
    set: MemberGroupSet
    groups: MemberGroup[]
}

/**
 * Whether a member of this type may be in the group: a group bound to no type takes everybody.
 *
 * @param group    the group
 * @param userType the member's type
 */
export function admits(group: MemberGroup, userType: string): boolean {
    const bound = group.userTypes ?? []
    return bound.length === 0 || (bound as string[]).includes(userType)
}

/** The member types a bound group takes, named as the screens name them and joined for a sentence. */
export function boundTypeNames(group: MemberGroup): string {
    return (group.userTypes ?? [])
        .map(type => StationUserTypeLabels[type as StationUserTypeName] ?? type)
        .join(', ')
}

/** The groups in no set, which a member joins and leaves one by one. */
export function groupsOutsideSets(groups: MemberGroup[]): MemberGroup[] {
    return groups.filter(group => group.groupSetId == null)
}

/** Every set that holds a group, with its groups, so each can be offered as one choice. */
export function setsWithGroups(groups: MemberGroup[], sets: MemberGroupSet[]): SetOfGroups[] {
    return sets
        .map(set => ({set, groups: groups.filter(group => group.groupSetId === set.id)}))
        .filter(entry => entry.groups.length > 0)
}

/**
 * The groups a member is in after choosing one group of a set, or none of them: whatever they were in
 * of that set is left, which is what makes the choice a move.
 *
 * @param selected the groups they are in now
 * @param entry    the set chosen in
 * @param groupId  the group chosen, or null for none of the set
 */
export function chooseInSet(selected: ReadonlySet<number>, entry: SetOfGroups, groupId: number | null): Set<number> {
    const next = new Set(selected)
    for (const group of entry.groups) next.delete(group.id)
    if (groupId !== null) next.add(groupId)
    return next
}

/** The groups a member is in after joining or leaving one group. */
export function toggled(selected: ReadonlySet<number>, groupId: number): Set<number> {
    const next = new Set(selected)
    if (next.has(groupId)) next.delete(groupId)
    else next.add(groupId)
    return next
}

/**
 * The group of the given set a member is in, where they are in one.
 *
 * @param memberGroupIds the groups the member is in
 * @param groups         every group of the station
 * @param setId          the set
 * @param exceptGroupId  a group to leave out, the one they are about to be put into
 */
export function groupOfSet(
    memberGroupIds: Iterable<number>,
    groups: MemberGroup[],
    setId: number | null | undefined,
    exceptGroupId?: number,
): MemberGroup | undefined {
    if (setId == null) return undefined
    const ids = new Set(memberGroupIds)
    return groups.find(group => group.groupSetId === setId && group.id !== exceptGroupId && ids.has(group.id))
}
