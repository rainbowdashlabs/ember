/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FailedMailCause, type FailedMail, type StationFailedMails} from '@/api/generated/schema'

/**
 * Two failed mails of the instance: one the queue gave up on, which can be sent again, and one the
 * provider reported as an address that does not exist, which cannot.
 */
export const SAMPLE_FAILED_MAILS: readonly FailedMail[] = [
    {
        id: 41, recipient: 'jana.berg@example.org', subject: 'Wähle dein Passwort für Ember',
        createdAt: '2026-11-10T08:12:00Z', failedAt: '2026-11-10T09:40:00Z',
        cause: FailedMailCause.RETRIES_EXHAUSTED, detail: null, requeueable: true,
    },
    {
        id: 37, recipient: 'tippfehler@example.org', subject: 'Bestätige deine E-Mail-Adresse',
        createdAt: '2026-11-09T17:03:00Z', failedAt: '2026-11-09T17:04:00Z',
        cause: FailedMailCause.HARD_BOUNCE, detail: '550 5.1.1 mailbox unavailable', requeueable: false,
    },
]

/** One station with failed mails, counted by cause, without recipients or subjects. */
export const SAMPLE_STATION_FAILURES: readonly StationFailedMails[] = [
    {
        stationUid: '00000000-0000-0000-0000-0000000000b1', stationName: 'Feuerwache Süd', count: 4,
        oldest: '2026-11-03T06:00:00Z', newest: '2026-11-08T18:30:00Z',
        causes: [{cause: FailedMailCause.NO_PROVIDER, count: 3}, {cause: FailedMailCause.BLOCKED, count: 1}],
    },
]
