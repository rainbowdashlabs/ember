/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {events} from '@/api'
import {RegistrationStatus, WhenFull} from '@/api/generated/schema'
import {useFullDateChoice} from '@/composables/useFullDateChoice'
import {describeFailure, type Failure} from '@/util/failure'
import type {Places} from '@/util/eventPlaces'
import type {QueueEntry} from '@/util/registrationQueue'

/**
 * What whoever decides the sign-ups of a date does with its places and its waiting list: accepting,
 * moving somebody up, putting sign-ups on the list and changing its order.
 *
 * <p>Accepting on a full date is refused by the server until it is told to go beyond the limit, so
 * the question is asked then and the acceptance sent again. Members of the station and partners'
 * members go through the routes of their kind.
 *
 * <p>Every action is answered for apart from the reload after it: an action the server took,
 * followed by a list that would not come back, is said as the list being stale rather than as the
 * action having failed.
 *
 * @param places  how the places of the date stand, for the question to name
 * @param reload  reads the sign-ups back after an action
 * @param failure the screen's failure channel
 */
export function useQueueActions(
    places: () => Places | null,
    reload: () => Promise<void>,
    failure: Ref<Failure | null>,
) {
    const {t} = useI18n()
    const fullDate = useFullDateChoice()
    const busy = ref(false)

    async function act(action: () => Promise<unknown>): Promise<boolean> {
        failure.value = null
        busy.value = true
        try {
            await action()
        } catch (e) {
            failure.value = describeFailure(e, t)
            return false
        } finally {
            busy.value = false
        }
        try {
            await reload()
        } catch (e) {
            failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
        }
        return true
    }

    /** Accepts a member's sign-up, asking first where that goes beyond the limit. */
    function accept(registrationId: number) {
        return act(() => fullDate.sendAsking('accept', places(), choice =>
            events.updateRegistrationStatus(registrationId, RegistrationStatus.ACCEPTED, choice === WhenFull.OVER_LIMIT)))
    }

    /** Accepts a partner's member's sign-up, asking first where that goes beyond the limit. */
    function acceptPartner(registrationId: number) {
        return act(() => fullDate.sendAsking('accept', places(), choice =>
            events.updateFederationRegistrationStatus(
                registrationId, RegistrationStatus.ACCEPTED, choice === WhenFull.OVER_LIMIT)))
    }

    /** Denies a member's or a partner's member's sign-up. */
    function deny(registrationId: number, partner = false) {
        return act(() => partner
            ? events.updateFederationRegistrationStatus(registrationId, RegistrationStatus.DENIED)
            : events.updateRegistrationStatus(registrationId, RegistrationStatus.DENIED))
    }

    /** Moves somebody on the waiting list up by hand, ahead of everybody before them. */
    function moveUp(entry: QueueEntry) {
        return entry.kind === 'partner' ? acceptPartner(entry.id) : accept(entry.id)
    }

    /** Puts somebody on the waiting list at another place in line. */
    function reorder(entry: QueueEntry, position: number) {
        return act(() => entry.kind === 'partner'
            ? events.moveFederationRegistrationInQueue(entry.id, position)
            : events.moveRegistrationInQueue(entry.id, position))
    }

    /** Puts members' sign-ups that wait for a decision onto the waiting list, in the order given. */
    function queue(registrationIds: number[]) {
        if (registrationIds.length === 0) return Promise.resolve(false)
        return act(() => events.moveRegistrationsToQueue(registrationIds))
    }

    /** Puts partners' sign-ups that wait for a decision onto the waiting list. */
    function queuePartners(registrationIds: number[]) {
        if (registrationIds.length === 0) return Promise.resolve(false)
        return act(() => events.moveFederationRegistrationsToQueue(registrationIds))
    }

    return {
        busy,
        fullDateQuestion: fullDate.question,
        answerFullDate: fullDate.answer,
        sendAsking: fullDate.sendAsking,
        accept,
        acceptPartner,
        deny,
        moveUp,
        reorder,
        queue,
        queuePartners,
    }
}
