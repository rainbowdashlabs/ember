/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    RegistrationStatus,
    type EnrichedFederationRegistration,
    type MemberIdentity,
    type RegistrationResponse,
} from '@/api/generated/schema'

/**
 * One sign-up on the waiting list of a date, a member's of the station or a partner's member's.
 *
 * <p>Both wait in the one list and move up in its order, so the screen draws them as one and sends
 * each action through the route of its kind.
 */
export interface QueueEntry {
    kind: 'member' | 'partner'
    /** The registration, of its kind. */
    id: number
    /** The place in line, 1 moving up next. */
    position: number
    identity: MemberIdentity | null
    name: string
    /** The partner station a partner's member signed up through, null for a member of the station. */
    partnerId: number | null
}

/**
 * The waiting list of a date, in the order it moves up in.
 *
 * <p>Every sign-up waiting on it carries its place in line, counted over the whole list, members of
 * the station and partners' members alike. The places are numbered 1 onwards once more, so a list a
 * reader sees only part of still reads as a list without holes.
 *
 * @param registrations the members' sign-ups of the date
 * @param partners      the partners' sign-ups of the date
 * @return everybody waiting, first in line first
 */
export function queueOf(
    registrations: RegistrationResponse[],
    partners: EnrichedFederationRegistration[],
): QueueEntry[] {
    const members: QueueEntry[] = registrations
        .filter(registration => registration.status === RegistrationStatus.QUEUED)
        .map(registration => ({
            kind: 'member',
            id: registration.id,
            position: registration.queuePosition ?? Number.MAX_SAFE_INTEGER,
            identity: registration.memberIdentity,
            name: registration.memberName,
            partnerId: null,
        }))
    const visitors: QueueEntry[] = partners
        .filter(entry => entry.registration.status === RegistrationStatus.QUEUED)
        .map(entry => ({
            kind: 'partner',
            id: entry.registration.id,
            position: entry.queuePosition ?? Number.MAX_SAFE_INTEGER,
            identity: entry.memberIdentity,
            name: entry.memberIdentity?.name ?? '',
            partnerId: entry.registration.partnerId,
        }))
    return [...members, ...visitors]
        .toSorted((a, b) => a.position - b.position)
        .map((entry, index) => ({...entry, position: index + 1}))
}

/** The place in line of a partner's sign-up on the waiting list, where it waits on it. */
export function partnerQueuePosition(queue: QueueEntry[], registrationId: number): number | null {
    return queue.find(entry => entry.kind === 'partner' && entry.id === registrationId)?.position ?? null
}
