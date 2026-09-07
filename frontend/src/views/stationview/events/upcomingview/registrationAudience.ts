/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AllEventRestrictions, RestrictionAudience} from '@/api/events'
import type {MemberGroup, StationUserTypeName, UserTag} from '@/api/types'
import {StationUserTypeLabels} from '@/api/types'

/** The slice of vue-i18n's translate this module needs: plain, interpolated and pluralised. */
type Translate = (key: string, arg?: number | Record<string, unknown>) => string

function sortedCopy(values: readonly number[] | undefined): number[] {
    return [...(values ?? [])].sort((a, b) => a - b)
}

function normalized(audience: RestrictionAudience | undefined) {
    return {
        userTypes: [...(audience?.userTypes ?? [])].sort(),
        groupIds: sortedCopy(audience?.groupIds),
        tagIds: sortedCopy(audience?.tagIds),
        memberIds: sortedCopy(audience?.memberIds),
    }
}

function isEmpty(audience: ReturnType<typeof normalized>): boolean {
    return audience.userTypes.length + audience.groupIds.length + audience.tagIds.length
        + audience.memberIds.length === 0
}

/**
 * Who may answer an appointment, as one readable sentence, or null where saying so would not help.
 *
 * <p>The sentence exists for the reader who sees an appointment and cannot answer it: seeing is
 * granted more widely than answering, so without a word they take the missing button for a bug.
 * Where both audiences are the same no such reader exists, because whoever is outside the answering
 * audience never sees the appointment, and where no answering audience is named at all everybody
 * who sees it may answer. Both cases say nothing.
 *
 * <p>Within one kind a member matches any of the named values, so those are joined with "oder";
 * the kinds themselves combine by the audience's own mode. Individually picked members always
 * grant on their own, whatever the mode, so they join with "oder" and are counted rather than
 * named: whoever resolves names needs a permission the reader of this sentence rarely has.
 */
export function registrationAudienceNote(
    restrictions: AllEventRestrictions,
    eventId: number,
    groups: MemberGroup[],
    tags: UserTag[],
    t: Translate,
): string | null {
    const entry = restrictions[eventId]
    if (!entry) return null
    const register = normalized(entry.register)
    if (isEmpty(register)) return null
    const view = normalized(entry.view)
    if (JSON.stringify(register) === JSON.stringify(view)) return null

    const or = t('eventsUpcoming.audienceOr')
    const parts: string[] = []
    const typeLabels = register.userTypes
        .map(ut => StationUserTypeLabels[ut as StationUserTypeName] ?? ut)
    if (typeLabels.length > 0) parts.push(typeLabels.join(or))
    const groupNames = register.groupIds.map(id => groups.find(g => g.id === id)?.name ?? `#${id}`)
    if (groupNames.length > 0) parts.push(groupNames.join(or))
    const tagNames = register.tagIds.map(id => tags.find(tag => tag.id === id)?.name ?? `#${id}`)
    if (tagNames.length > 0) parts.push(tagNames.join(or))

    const mode = entry.register.mode ?? 'AND'
    let audience = parts.join(mode === 'OR' ? or : t('eventsUpcoming.audienceAnd'))
    if (register.memberIds.length > 0) {
        const members = register.memberIds.length === 1
            ? t('eventsUpcoming.audienceMemberOne')
            : t('eventsUpcoming.audienceMemberMany', {n: register.memberIds.length})
        audience = audience ? audience + or + members : members
    }
    return t('eventsUpcoming.registrationAudience', {audience})
}
