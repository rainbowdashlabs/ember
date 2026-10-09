/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {notificationMessageKey} from './notificationMessageKey'

/**
 * Which sentence a notification is worded by.
 */
describe('notificationMessageKey', () => {
    it('keeps the key a type with one sentence arrives with', () => {
        expect(notificationMessageKey('EVENT_CANCELLED', 'notification.eventCancelled', {eventName: 'Dienst'}))
            .toBe('notification.eventCancelled')
    })

    it('names the date where only one date was called off', () => {
        expect(notificationMessageKey('EVENT_CANCELLED', 'notification.eventCancelled',
            {eventName: 'Dienst', eventDate: '2026-10-07', cause: 'MANUAL'}))
            .toBe('notification.eventDateCancelled')
    })

    it('says too few registered where the check called the date off', () => {
        expect(notificationMessageKey('EVENT_CANCELLED', 'notification.eventCancelled',
            {eventName: 'Dienst', eventDate: '2026-10-07', cause: 'THRESHOLD'}))
            .toBe('notification.eventDateCancelledTooFew')
    })

    it('names the next date of a moved appointment where one is left', () => {
        expect(notificationMessageKey('EVENT_DATE_DROPPED', 'notification.eventDateDropped', {nextDate: '2026-10-07'}))
            .toBe('notification.eventDateDropped')
    })

    it('says no later date follows where none is left', () => {
        expect(notificationMessageKey('EVENT_DATE_DROPPED', 'notification.eventDateDropped', {eventDate: '2026-10-06'}))
            .toBe('notification.eventDateDroppedLast')
    })

    it('counts an expiry reminder the way its kind is counted', () => {
        expect(notificationMessageKey('EXPIRY_REMINDER', 'notification.expiryReminder', {kind: 'EXPIRES_IN', days: '1'}))
            .toBe('notification.expiryReminderInOne')
    })

    it('gives a declined name its reason where the decider wrote one', () => {
        expect(notificationMessageKey('NAME_CHANGE_DENIED', 'notification.nameChangeDenied',
            {requestedName: 'Mia Neu', reason: 'Bitte mit Ausweis'}))
            .toBe('notification.nameChangeDeniedWithReason')
        expect(notificationMessageKey('NAME_CHANGE_DENIED', 'notification.nameChangeDenied', {requestedName: 'Mia Neu'}))
            .toBe('notification.nameChangeDenied')
    })

    it('names the member of a signed document only where somebody else signed it', () => {
        expect(notificationMessageKey('DOCUMENT_SIGNED', 'notification.documentSigned',
            {documentTitle: 'Einverständnis', signerName: 'Eva Muster', memberName: 'Mia Muster'}))
            .toBe('notification.documentSignedForMember')
        expect(notificationMessageKey('DOCUMENT_SIGNED', 'notification.documentSigned',
            {documentTitle: 'Einverständnis', signerName: 'Mia Muster'}))
            .toBe('notification.documentSigned')
    })

    it('names the member of a withdrawal at a partner station only where the partner shares the name', () => {
        expect(notificationMessageKey('PARTNER_SIGNATURE_WITHDRAWN', 'notification.partnerSignatureWithdrawn',
            {documentTitle: 'Einverständnis', stationName: 'Wache Nord', memberName: 'Mia Muster'}))
            .toBe('notification.partnerSignatureWithdrawnNamed')
        expect(notificationMessageKey('PARTNER_SIGNATURE_WITHDRAWN', 'notification.partnerSignatureWithdrawn',
            {documentTitle: 'Einverständnis', stationName: 'Wache Nord'}))
            .toBe('notification.partnerSignatureWithdrawn')
    })
})
