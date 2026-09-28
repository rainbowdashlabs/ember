/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {isRecurringEvent, type EventRegistrationEntry, type FederatedEventRegistration, type StationEvent} from '@/api/events'
import {rowsOnDate} from '@/util/eventAnswers'

/**
 * The sign-ups of the one occurrence a screen is looking at.
 *
 * <p>The lists are loaded for every date a repeating appointment ever had, and whatever reads them
 * reads the filtered side only, so a member's answer, the waiting list and the counts all belong to
 * the date that is open. A one-off has no other date, so its rows are taken as they come.
 *
 * @param event         the appointment
 * @param effectiveDate the occurrence in view, or null where none is known
 */
export function useRegistrationsInView(event: () => StationEvent, effectiveDate: () => string | null) {
    const loadedRegistrations = ref<EventRegistrationEntry[]>([])
    const loadedFederatedRegs = ref<FederatedEventRegistration[]>([])

    const dateInView = computed(() => (isRecurringEvent(event().eventType) ? effectiveDate() : null))

    const registrations = computed(() =>
        rowsOnDate(loadedRegistrations.value, dateInView.value, registration => registration.eventDate))

    const federatedRegs = computed(() =>
        rowsOnDate(loadedFederatedRegs.value, dateInView.value, entry => entry.registration.eventDate))

    return {loadedRegistrations, loadedFederatedRegs, registrations, federatedRegs}
}
