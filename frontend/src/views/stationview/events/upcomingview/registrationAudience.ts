/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AllEventRestrictions} from '@/api/events'
import type {MemberGroup, UserTag} from '@/api/generated/schema'
import {namesSomebody, normalizedRestriction, restrictionLabels} from '@/components/input/restriction'

/** The slice of vue-i18n's translate this module needs: plain, interpolated and pluralised. */
type Translate = (key: string, arg?: number | Record<string, unknown>) => string

/**
 * Who may answer an appointment, as one readable sentence, or null where saying so would not help.
 *
 * <p>The sentence exists for the reader who sees an appointment and cannot answer it: seeing is
 * granted more widely than answering, so without a word they take the missing button for a bug.
 * Where both audiences are the same no such reader exists, because whoever is outside the answering
 * audience never sees the appointment, and where no answering audience is named at all everybody
 * who sees it may answer. Both cases say nothing.
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
    const register = normalizedRestriction(entry.register)
    if (!namesSomebody(register)) return null
    const view = normalizedRestriction(entry.view)
    if (JSON.stringify(register) === JSON.stringify(view)) return null
    return t('eventsUpcoming.registrationAudience', {audience: audienceText(entry.register, groups, tags, t)})
}

/**
 * Who may see an appointment kept from some of the station, as one readable sentence, or null where
 * everybody sees it. It goes with the lock a kept appointment carries, so the lock does not leave the
 * reader guessing whom it keeps out.
 */
export function viewAudienceNote(
    restrictions: AllEventRestrictions,
    eventId: number,
    groups: MemberGroup[],
    tags: UserTag[],
    t: Translate,
): string | null {
    const entry = restrictions[eventId]
    if (!entry || !namesSomebody(normalizedRestriction(entry.view))) return null
    return t('eventsUpcoming.viewAudience', {audience: audienceText(entry.view, groups, tags, t)})
}

/**
 * The audience as words. Within one kind a member matches any of the named values, so those are
 * joined with "oder"; the kinds themselves combine by the audience's own mode. Individually picked
 * members always grant on their own, whatever the mode, so they join with "oder" and are counted
 * rather than named: whoever resolves names needs a permission the reader of this sentence rarely
 * has.
 */
function audienceText(
    restriction: AllEventRestrictions[number]['register'],
    groups: MemberGroup[],
    tags: UserTag[],
    t: Translate,
): string {
    const normalized = normalizedRestriction(restriction)
    const or = t('eventsUpcoming.audienceOr')
    const labels = restrictionLabels(normalized, groups, tags)
    const parts = [labels.userTypes, labels.groups, labels.tags]
        .filter(names => names.length > 0)
        .map(names => names.join(or))

    let audience = parts.join(restriction.mode === 'OR' ? or : t('eventsUpcoming.audienceAnd'))
    if (normalized.memberIds.length > 0) {
        const members = normalized.memberIds.length === 1
            ? t('eventsUpcoming.audienceMemberOne')
            : t('eventsUpcoming.audienceMemberMany', {n: normalized.memberIds.length})
        audience = audience ? audience + or + members : members
    }
    return audience
}
