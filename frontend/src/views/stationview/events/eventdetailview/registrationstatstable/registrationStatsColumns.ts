/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventRegistrationEntry, MemberRegistrationStats} from '@/api/events'
import {ColumnTypes, type TableColumn} from '@/components/table/tableColumn'

/** One sign-up on the ranking, with how often its member has been let in before, where that is known. */
export interface RankedRegistration {
    registration: EventRegistrationEntry
    stats: MemberRegistrationStats | null
}

export const SCORE_KEY = 'score'

/** How much of the member's sign-ups were accepted, in whole percent. */
function acceptPercent(row: RankedRegistration): number | null {
    return row.stats ? Math.round(row.stats.acceptRate * 100) : null
}

/**
 * The columns of the ranking: the member, the score it is ranked by, how often they were let in and
 * turned away, the share of places they got, and the evening they asked for.
 */
export function registrationStatsColumns(t: (key: string) => string): TableColumn<RankedRegistration>[] {
    return [
        {
            key: 'member',
            label: t('registrationStats.member'),
            type: ColumnTypes.TEXT,
            value: row => row.registration.memberIdentity?.name ?? row.registration.memberName,
            pinned: true,
        },
        {key: SCORE_KEY, label: t('registrationStats.score'), type: ColumnTypes.NUMBER, value: row => row.stats?.fairnessScore, align: 'center'},
        {key: 'accepted', label: t('registrationStats.accepted'), type: ColumnTypes.NUMBER, value: row => row.stats?.accepted, align: 'center'},
        {key: 'denied', label: t('registrationStats.denied'), type: ColumnTypes.NUMBER, value: row => row.stats?.denied, align: 'center'},
        {
            key: 'rate',
            label: t('registrationStats.rate'),
            type: ColumnTypes.NUMBER,
            value: acceptPercent,
            display: row => row.stats ? `${acceptPercent(row)}%` : '',
            align: 'center',
        },
        {key: 'date', label: t('eventsRegistrations.date'), type: ColumnTypes.DATE, value: row => row.registration.eventDate, align: 'center'},
    ]
}
