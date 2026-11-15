/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AllEventRestrictions} from '@/api/events'
import type {MemberGroup, UserTag} from '@/api/generated/schema'
import {audienceWords, namesSomebody, normalizedRestriction} from '@/components/input/restriction'

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
    return t('eventsUpcoming.registrationAudience', {audience: audienceWords(entry.register, groups, tags, t)})
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
    return t('eventsUpcoming.viewAudience', {audience: audienceWords(entry.view, groups, tags, t)})
}
