/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {EventTypes} from '@/api/events'

const LABEL_KEYS: Record<string, string> = {
    [EventTypes.RECURRING]: 'events.typeRecurring',
    [EventTypes.MONTHLY_FIRST]: 'events.typeMonthlyFirst',
    [EventTypes.QUARTERLY]: 'events.typeQuarterly',
    [EventTypes.YEARLY]: 'events.typeYearly',
}

/**
 * The translation key naming how often an appointment takes place.
 *
 * <p>Every screen that names the kind of an appointment reads it from here, so the list and the
 * detail page cannot call the same series by two different names. Anything that is not a known
 * series is a one-off.
 */
export function eventTypeLabelKey(eventType?: string): string {
    return (eventType && LABEL_KEYS[eventType]) || 'events.typeOneTime'
}
