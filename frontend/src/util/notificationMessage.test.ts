/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {NotificationType, StationModule, WaitingListAnswer} from '@/api/generated/schema'
import {notificationMessage} from './notificationMessage'

const LABELS: Record<string, string> = {
    'notification.waitlistAnswer.COMING': 'Ich komme',
    'stationManage.moduleLostAndFound': 'Fundbüro',
}

/** Answers a label where one is known and otherwise the key with what it was handed, so a test sees both. */
function t(key: string, params?: Record<string, string>): string {
    return LABELS[key] ?? `${key} ${JSON.stringify(params ?? {})}`
}

/**
 * How a notification reads in the app: the enum values its parameters carry are written as words.
 */
describe('notificationMessage', () => {
    it('writes the answer to a waiting-list invitation as a word', () => {
        const message = notificationMessage(t, {
            type: NotificationType.WAITLIST_INVITATION_ANSWERED,
            localeKey: 'notification.waitlistInvitationAnswered',
            params: {childName: 'Mara Seeger', listName: 'Jugendfeuerwehr', answer: WaitingListAnswer.COMING},
        })
        expect(message).toContain('"answer":"Ich komme"')
        expect(message).not.toContain(WaitingListAnswer.COMING)
    })

    it('writes the module a cluster switched off by its name', () => {
        const message = notificationMessage(t, {
            type: NotificationType.CLUSTER_MODULE_DENIED,
            localeKey: 'notification.clusterModuleDenied',
            params: {clusterName: 'Kreis', module: StationModule.LOST_AND_FOUND},
        })
        expect(message).toContain('"module":"Fundbüro"')
    })

    it('words a whole event group called off as an event group', () => {
        const message = notificationMessage(t, {
            type: NotificationType.EVENT_CANCELLED,
            localeKey: 'notification.eventCancelled',
            params: {eventName: 'Jugendflamme 1', eventGroup: 'true'},
        })
        expect(message.startsWith('notification.eventGroupCancelled')).toBe(true)
    })

    it('leaves the parameters of other types as they arrived', () => {
        const message = notificationMessage(t, {
            type: NotificationType.WAITLIST_NEW_ENTRY,
            localeKey: 'notification.waitlistNewEntry',
            params: {childName: 'Mara Seeger', listName: 'Jugendfeuerwehr'},
        })
        expect(message).toBe('notification.waitlistNewEntry {"childName":"Mara Seeger","listName":"Jugendfeuerwehr"}')
    })
})
