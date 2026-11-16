/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {RegistrationStatus} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'

/**
 * The word for what somebody answered, the same wherever an answer is named: a badge, a filter or a
 * column. Declined and withdrawn read alike, as the badge has always shown them.
 *
 * @param status the answer
 * @param t      the translator
 * @return the word for it
 */
export function registrationStatusLabel(status: RegistrationStatus | string, t: Translate): string {
    if (status === RegistrationStatus.ACCEPTED) return t('eventsUpcoming.statusAccepted')
    if (status === RegistrationStatus.PENDING) return t('eventsUpcoming.statusPending')
    if (status === RegistrationStatus.DENIED) return t('eventsUpcoming.statusDenied')
    return t('eventsUpcoming.statusDeclined')
}
