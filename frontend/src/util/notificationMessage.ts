/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {NotificationType} from '@/api/generated/schema'
import {STATION_MODULE_OPTIONS} from '@/data/stationModules'
import {formatDate} from './format'
import {notificationMessageKey} from './notificationMessageKey'

type Translate = (key: string, params?: Record<string, string>) => string

/** What a notification carries that its sentence is built from. */
export interface NotificationMessageSource {
    type: NotificationType
    localeKey: string
    params: Record<string, string>
}

/** A day as the database writes one, which is not how anybody here reads one. */
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/

/**
 * The days in a notification's parameters, written the way the rest of the product writes them.
 *
 * <p>A date reaches the screen as the plain day it is stored as, and four kinds of notification
 * carry one: an appointment coming up, a batch of new appointments, an answer still wanted, a check
 * to hand back. Every one of them read "2026-09-19" in the middle of a German sentence. Done here
 * rather than at each of the four, because the next notification to carry a date would otherwise
 * read that way too until somebody noticed.
 */
function withReadableDates(params: Record<string, string>): Record<string, string> {
    return Object.fromEntries(
        Object.entries(params).map(([key, value]) =>
            [key, typeof value === 'string' && ISO_DATE.test(value) ? formatDate(value) : value]),
    )
}

/** The name a station module goes by on its own page, the bare value for one nobody named. */
function moduleLabel(t: Translate, module: string): string {
    const option = STATION_MODULE_OPTIONS.find(candidate => candidate.value === module)
    return option ? t(option.labelKey) : module
}

/**
 * The parameters of a notification with every enum value they carry written as a word: the status
 * of a registration or a lending request, the parts of a changed appointment, the answer to a
 * waiting-list invitation and the module a cluster switched off.
 */
function readableParams(t: Translate, type: NotificationType, raw: Record<string, string>): Record<string, string> {
    const params = withReadableDates(raw)
    if (type === NotificationType.EVENT_REGISTRATION_STATUS && params.status) {
        params.status = t(`dashboard.registrationStatus.${params.status}`)
    }
    if (type === NotificationType.LENDING_STATUS_CHANGE && params.status) {
        params.status = t(`dashboard.lendingStatus.${params.status}`)
    }
    if (type === NotificationType.EVENT_CHANGED && params.changedParts) {
        params.changedParts = params.changedParts.split(',').map(part => t(`notification.changedPart.${part}`)).join(', ')
    }
    if (type === NotificationType.WAITLIST_INVITATION_ANSWERED && params.answer) {
        params.answer = t(`notification.waitlistAnswer.${params.answer}`)
    }
    if (type === NotificationType.CLUSTER_MODULE_DENIED && params.module) {
        params.module = moduleLabel(t, params.module)
    }
    return params
}

/**
 * The sentence for a notification, as every list of notifications in the app shows it.
 *
 * <p>Most of it is the message the type carries, with the enums in its parameters routed through
 * their locale namespace so they read in German. A movement called off is the exception with two
 * sentences rather than one: whether the piece came home cannot be said in a word, and it is the
 * half the reader cannot guess.
 *
 * @param t            the translation function of the calling component
 * @param notification the notification to word
 */
export function notificationMessage(t: Translate, notification: NotificationMessageSource): string {
    const params = readableParams(t, notification.type, notification.params)
    if (notification.type === NotificationType.MOVEMENT_CANCELLED) {
        const key = params.itemStayedAway === 'true' ? 'movementCancelledAway' : 'movementCancelled'
        return t(`notification.${key}`, params)
    }
    return t(notificationMessageKey(notification.type, notification.localeKey, params), params)
}
