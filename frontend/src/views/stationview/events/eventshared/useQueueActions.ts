/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {shiftIsHeld} from '@/util/modifierKeys'
import {RegistrationStatus, WhenFull} from '@/api/generated/schema'
import type {EnrichedFederationRegistration, RegistrationResponse} from '@/api/generated/schema'
import {appointmentQueueRoutes, type QueueRoutes} from './queueRoutes'
import {useFullDateChoice} from '@/composables/useFullDateChoice'
import {describeFailure, type Failure} from '@/util/failure'
import {showToast} from '@/util/toast'
import type {Places} from '@/util/eventPlaces'
import type {QueueEntry} from '@/util/registrationQueue'

/** Reads the status of an appointment date's sign-ups, the station's own and its partners', for {@link useQueueActions}. */
export function appointmentStatuses(
    registrations: () => RegistrationResponse[],
    partners: () => EnrichedFederationRegistration[],
) {
    return (id: number, partner: boolean) => partner
        ? partners().find(entry => entry.registration.id === id)?.registration.status
        : registrations().find(entry => entry.id === id)?.status
}

/**
 * What whoever decides the sign-ups of a date or an event group does with its places and its waiting
 * list: accepting, moving somebody up, putting sign-ups on the list and changing its order.
 *
 * <p>Accepting on a full date is refused by the server until the request allows going beyond the limit, so
 * the question is asked then and the acceptance sent again. Members of the station and partners'
 * members go through the routes of their kind.
 *
 * <p>Every action is answered for apart from the reload after it: an action the server took,
 * followed by a list that would not come back, is reported as the list being stale rather than as the
 * action having failed. Accepting and denying show a message once they went through, and denying somebody
 * who already holds a place asks first, since it takes that place away.
 *
 * @param places   how the places of the date stand, for the question to name
 * @param reload   reads the sign-ups back after an action
 * @param failure  the screen's failure channel
 * @param routes   where the actions go, an appointment date's sign-ups unless set otherwise
 * @param statusOf the status a sign-up has now, by its id and whether it is a partner's
 */
export function useQueueActions(
    places: () => Places | null,
    reload: () => Promise<void>,
    failure: Ref<Failure | null>,
    routes: QueueRoutes = appointmentQueueRoutes,
    statusOf: (id: number, partner: boolean) => RegistrationStatus | null | undefined = () => undefined,
) {
    const {t} = useI18n()
    const fullDate = useFullDateChoice()
    const busy = ref(false)
    const denyQuestion = ref(false)
    let settleDeny: ((denied: boolean) => void) | null = null

    /**
     * Asks whether a sign-up that holds a place is to be denied, and answers with the choice.
     * Shift answers yes at once, as it does for every plain confirmation.
     */
    function askDeny(): Promise<boolean> {
        if (shiftIsHeld()) return Promise.resolve(true)
        settleDeny?.(false)
        denyQuestion.value = true
        return new Promise(resolve => {
            settleDeny = resolve
        })
    }

    /** Takes the answer to {@link askDeny}. */
    function answerDeny(denied: boolean) {
        denyQuestion.value = false
        const resolve = settleDeny
        settleDeny = null
        resolve?.(denied)
    }

    /** Runs an action and, once it went through, reports what happened. */
    async function actSaying(action: () => Promise<unknown>, said: string): Promise<boolean> {
        const done = await act(action)
        if (done) showToast(said, 'success')
        return done
    }

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
    function accept(id: number) {
        return acceptSending(() => fullDate.sendAsking('accept', places(), choice =>
            routes.decide(id, RegistrationStatus.ACCEPTED, choice === WhenFull.OVER_LIMIT)))
    }

    /** Accepts a partner's member's sign-up, asking first where that goes beyond the limit. */
    function acceptPartner(id: number) {
        return acceptSending(() => fullDate.sendAsking('accept', places(), choice =>
            routes.decidePartner(id, RegistrationStatus.ACCEPTED, choice === WhenFull.OVER_LIMIT)))
    }

    /** Sends an acceptance and reports that it went through, unless the question about the limit was turned down. */
    async function acceptSending(send: () => Promise<boolean>): Promise<boolean> {
        let sent = false
        const done = await act(async () => {
            sent = await send()
        })
        if (done && sent) showToast(t('registrationQueue.accepted'), 'success')
        return done && sent
    }

    /** Denies a member's or a partner's member's sign-up, asking first where it holds a place. */
    async function deny(id: number, partner = false) {
        if (statusOf(id, partner) === RegistrationStatus.ACCEPTED && !await askDeny()) return false
        return actSaying(() => partner
            ? routes.decidePartner(id, RegistrationStatus.DENIED)
            : routes.decide(id, RegistrationStatus.DENIED), t('registrationQueue.denied'))
    }

    /** Moves somebody on the waiting list up by hand, ahead of everybody before them. */
    function moveUp(entry: QueueEntry) {
        return entry.kind === 'partner' ? acceptPartner(entry.id) : accept(entry.id)
    }

    /** Puts somebody on the waiting list at another place in line. */
    function reorder(entry: QueueEntry, position: number) {
        return act(() => entry.kind === 'partner' ? routes.movePartner(entry.id, position) : routes.move(entry.id, position))
    }

    /** Puts members' sign-ups that wait for a decision onto the waiting list, in the order given. */
    function queue(ids: number[]) {
        if (ids.length === 0) return Promise.resolve(false)
        return act(() => routes.queue(ids))
    }

    /** Puts partners' sign-ups that wait for a decision onto the waiting list. */
    function queuePartners(ids: number[]) {
        if (ids.length === 0) return Promise.resolve(false)
        return act(() => routes.queuePartners(ids))
    }

    return {
        busy,
        fullDateQuestion: fullDate.question,
        answerFullDate: fullDate.answer,
        denyQuestion,
        answerDeny,
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
