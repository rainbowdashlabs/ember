/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FailedMailCause, type FailedMail, type StationFailedMails} from '@/api/generated/schema'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'

/** Every cause in the order a reader meets them: the queue's own reasons, then what providers report. */
const CAUSES: readonly FailedMailCause[] = [
    FailedMailCause.NO_PROVIDER,
    FailedMailCause.NO_PROVIDER_LEFT,
    FailedMailCause.PROVIDER_REFUSED,
    FailedMailCause.PROVIDER_BROKEN,
    FailedMailCause.RETRIES_EXHAUSTED,
    FailedMailCause.UNRECORDED,
    FailedMailCause.HARD_BOUNCE,
    FailedMailCause.BLOCKED,
    FailedMailCause.SPAM,
]

/** The causes of a station's failed mails in one line, most frequent first, each with its count. */
export function causeSummary(group: StationFailedMails, t: (key: string) => string): string {
    return group.causes.map(entry => `${t(`failedMails.cause.${entry.cause}`)} (${entry.count})`).join(', ')
}

/** The columns of the instance's own failed mails. The reason is the provider's own words where it gave any. */
export function failedMailColumns(t: (key: string) => string): TableColumn<FailedMail>[] {
    const causes = enumOptions(CAUSES, value => t(`failedMails.cause.${value}`))
    return [
        {key: 'recipient', label: t('failedMails.colRecipient'), type: ColumnTypes.TEXT, value: mail => mail.recipient, pinned: true},
        {key: 'subject', label: t('failedMails.colSubject'), type: ColumnTypes.TEXT, value: mail => mail.subject},
        {key: 'createdAt', label: t('failedMails.colCreated'), type: ColumnTypes.DATE_TIME, value: mail => mail.createdAt},
        {key: 'failedAt', label: t('failedMails.colFailed'), type: ColumnTypes.DATE_TIME, value: mail => mail.failedAt},
        {key: 'cause', label: t('failedMails.colCause'), type: ColumnTypes.ENUM, value: mail => mail.cause, options: causes},
        {key: 'detail', label: t('failedMails.colDetail'), type: ColumnTypes.TEXT, value: mail => mail.detail},
    ]
}

/** The columns of the stations' failed mails: how many and why, never whom they were for. */
export function stationFailedMailColumns(t: (key: string) => string): TableColumn<StationFailedMails>[] {
    return [
        {key: 'station', label: t('failedMails.colStation'), type: ColumnTypes.TEXT, value: group => group.stationName, pinned: true},
        {key: 'count', label: t('failedMails.colCount'), type: ColumnTypes.NUMBER, value: group => group.count},
        {key: 'causes', label: t('failedMails.colCauses'), type: ColumnTypes.TEXT, value: group => causeSummary(group, t)},
        {key: 'oldest', label: t('failedMails.colOldest'), type: ColumnTypes.DATE_TIME, value: group => group.oldest},
        {key: 'newest', label: t('failedMails.colNewest'), type: ColumnTypes.DATE_TIME, value: group => group.newest},
    ]
}
