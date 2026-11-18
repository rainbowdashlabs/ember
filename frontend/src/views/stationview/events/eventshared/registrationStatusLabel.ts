/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {RegistrationStatus} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'

/** What is known about a place on the waiting list, beyond the status itself. */
export interface QueueContext {
    /** The place in line, 1 moving up next, where the sign-up waits. */
    queuePosition?: number | null
    /** Whether the date is already over, so whoever still waits did not get a place. */
    past?: boolean
}

/**
 * The word for what somebody answered, the same wherever an answer is named: a badge, a filter or a
 * column. Declined and withdrawn read alike, as the badge has always shown them.
 *
 * <p>A sign-up on the waiting list says its place in line where it is known, and once the date is
 * over that nobody moved it up: it did not get a place.
 *
 * @param status the answer
 * @param t      the translator
 * @param queue  the place in line and whether the date is over, for a sign-up on the waiting list
 * @return the word for it
 */
export function registrationStatusLabel(status: RegistrationStatus | string, t: Translate, queue: QueueContext = {}): string {
    if (status === RegistrationStatus.ACCEPTED) return t('eventsUpcoming.statusAccepted')
    if (status === RegistrationStatus.PENDING) return t('eventsUpcoming.statusPending')
    if (status === RegistrationStatus.DENIED) return t('eventsUpcoming.statusDenied')
    if (status === RegistrationStatus.QUEUED) return queuedLabel(t, queue)
    return t('eventsUpcoming.statusDeclined')
}

/** What a sign-up on the waiting list reads as: its place in line, or that it got no place. */
function queuedLabel(t: Translate, queue: QueueContext): string {
    if (queue.past) return t('registrationQueue.noPlace')
    if (queue.queuePosition != null) return t('registrationQueue.statusAt', {position: queue.queuePosition})
    return t('registrationQueue.status')
}
