/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    RegistrationStatus,
    type RegistrationResponse,
    type RegistrationStatsResponse,
} from '@/api/generated/schema'

/** The sign-ups of one status, as the sign-up list draws them under one heading. */
export interface StatusGroup {
    status: string
    entries: RegistrationResponse[]
}

/** The statuses drawn as groups of their own, in the order they are drawn. */
const DECIDED_ORDER: readonly RegistrationStatus[] = [
    RegistrationStatus.ACCEPTED,
    RegistrationStatus.DECLINED,
    RegistrationStatus.DENIED,
    RegistrationStatus.WITHDRAWN,
]

/**
 * The sign-ups waiting for a decision, the strongest claim to a place first: the highest fairness
 * score leads.
 */
export function pendingByFairness(
    registrations: RegistrationResponse[],
    stats: RegistrationStatsResponse[],
): RegistrationResponse[] {
    const scoreOf = (registration: RegistrationResponse) =>
        stats.find(entry => entry.memberId === registration.memberId)?.fairnessScore ?? 0
    return registrations
        .filter(registration => registration.status === RegistrationStatus.PENDING)
        .toSorted((a, b) => scoreOf(b) - scoreOf(a))
}

/**
 * The decided sign-ups by status, each group by name. Sign-ups waiting for a decision and those on
 * the waiting list are drawn apart, in an order of their own, so they are left out here.
 */
export function decidedByStatus(registrations: RegistrationResponse[], locale: string): StatusGroup[] {
    return DECIDED_ORDER
        .map(status => ({
            status,
            entries: registrations
                .filter(registration => registration.status === status)
                .toSorted((a, b) => a.memberName.localeCompare(b.memberName, locale)),
        }))
        .filter(group => group.entries.length > 0)
}

/**
 * The sign-ups that can go onto the waiting list, oldest first, which is the order the list takes
 * them in. A place a question of the appointment gives is no sign-up anybody made, and the server
 * refuses the whole batch for one, so those are left out.
 *
 * @return the registration ids
 */
export function queueableByAge(registrations: RegistrationResponse[]): number[] {
    return registrations
        .filter(registration => !registration.fromField)
        .toSorted((a, b) => a.createdAt.localeCompare(b.createdAt))
        .map(registration => registration.id)
}
