/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {MailDeliveryStatus, MailQueueStatus, type MailRecord} from '@/api/mailProviders'
import {ColumnTypes, enumOptions, type TableColumn} from '@/components/table/tableColumn'

/** Delivery states that mean the mail did not arrive, which is what a reader is scanning for. */
const UNDELIVERED: readonly string[] = [
    MailDeliveryStatus.SOFT_BOUNCE, MailDeliveryStatus.HARD_BOUNCE, MailDeliveryStatus.BLOCKED, MailDeliveryStatus.SPAM,
]

/**
 * Waiting and unable to go anywhere. Worth saying outright: a queue that is merely busy looks the
 * same as one that will never move, and only one of them needs somebody to act.
 */
export function isStuck(mail: MailRecord): boolean {
    return (mail.status === MailQueueStatus.PENDING || mail.status === MailQueueStatus.SENDING) && !mail.reachable
}

/** Whether the mail went wrong somewhere: refused, given up on, or stuck. */
export function isTroubled(mail: MailRecord): boolean {
    return UNDELIVERED.includes(mail.deliveryStatus) || mail.status === MailQueueStatus.FAILED || isStuck(mail)
}

/** The words for each place in the queue, in the order a mail passes through them. */
const QUEUE_LABEL_KEYS: Record<string, string> = {
    [MailQueueStatus.PENDING]: 'mailDashboard.pending',
    [MailQueueStatus.SENDING]: 'mailDashboard.sending',
    [MailQueueStatus.SENT]: 'mailDashboard.sent',
    [MailQueueStatus.FAILED]: 'mailDashboard.failed',
}

/** The moment that says most about a mail: when it was handed over, or else when it was written. */
function momentOf(mail: MailRecord): string {
    return mail.sentAt ?? mail.createdAt
}

/**
 * The columns of a list of mails.
 *
 * <p>A provider accepting a message and a message arriving are two different things, so both show:
 * the queue status says whether Ember got rid of it, the delivery status says what the provider
 * reported afterwards. The reason a provider gave is worth more than either.
 */
export function mailRecordColumns(t: (key: string) => string): TableColumn<MailRecord>[] {
    const queueStates = enumOptions(Object.keys(QUEUE_LABEL_KEYS), value => t(QUEUE_LABEL_KEYS[value]!))
    const deliveryStates = enumOptions(Object.values(MailDeliveryStatus), value => t(`mailDashboard.delivery.${value}`))
    return [
        {key: 'recipient', label: t('mailDashboard.colRecipient'), type: ColumnTypes.TEXT, value: mail => mail.recipient, pinned: true},
        {key: 'subject', label: t('mailDashboard.colSubject'), type: ColumnTypes.TEXT, value: mail => mail.subject},
        {key: 'when', label: t('mailDashboard.colWhen'), type: ColumnTypes.DATE_TIME, value: momentOf},
        {key: 'status', label: t('mailDashboard.colStatus'), type: ColumnTypes.ENUM, value: mail => mail.status, options: queueStates},
        {
            key: 'deliveryStatus', label: t('mailDashboard.colDelivery'), type: ColumnTypes.ENUM,
            value: mail => mail.deliveryStatus === MailDeliveryStatus.UNKNOWN ? null : mail.deliveryStatus, options: deliveryStates,
        },
        {key: 'provider', label: t('mailDashboard.colProvider'), type: ColumnTypes.NUMBER, value: mail => mail.providerPosition + 1},
        {key: 'attempts', label: t('mailDashboard.colAttempts'), type: ColumnTypes.NUMBER, value: mail => mail.attempts || null},
        {key: 'detail', label: t('mailDashboard.colDetail'), type: ColumnTypes.TEXT, value: mail => mail.deliveryDetail},
    ]
}
