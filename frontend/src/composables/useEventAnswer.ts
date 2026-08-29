/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {events} from '@/api'
import type {EventRegistrationField, RegistrationFieldValue, StationEvent} from '@/api/events'
import {useSidebarCounts} from '@/composables/useSidebarCounts'

/**
 * Answering one appointment: signing up, refusing, and taking either back.
 *
 * <p>Held apart from any one screen because the same answer is given in more than one place: from
 * the list of what is coming up, and from the appointment's own page. Both have to ask the
 * appointment's questions before they can sign anybody up, and a second copy of that would be a
 * second chance to get it wrong.
 *
 * @param currentMemberId the acting member, whose own answer is sent without naming an id
 * @param afterChange     run once an answer has landed, to reload whatever the screen shows
 * @param error           the screen's error channel, written to rather than thrown at
 */
export function useEventAnswer(
    currentMemberId: Ref<number>,
    afterChange: () => Promise<void>,
    error: Ref<string>,
) {
    const {t} = useI18n()
    const {refresh: refreshSidebarCounts} = useSidebarCounts()

    /** Which answer is in flight, as `event-date-member`, so one row can show it turning. */
    const registering = ref<string | null>(null)

    /**
     * A sign-up waiting for its answers. Set when the appointment asks questions, cleared once the
     * dialog the screen renders for it is confirmed or dismissed.
     */
    const fieldPrompt = ref<{
        event: StationEvent
        date: string
        memberId: number
        fields: EventRegistrationField[]
    } | null>(null)

    async function changeRegistration(action: () => Promise<unknown>) {
        error.value = ''
        try {
            await action()
            await afterChange()
            refreshSidebarCounts()
        } catch {
            error.value = t('common.error')
        }
    }

    /** The member id to send: omitted for the acting member, explicit for a managed one. */
    function memberIdParam(memberId: number): number | undefined {
        return memberId !== currentMemberId.value ? memberId : undefined
    }

    async function sendRegistration(
        ev: StationEvent,
        date: string,
        memberId: number,
        fields?: RegistrationFieldValue[],
    ) {
        registering.value = `${ev.id}-${date}-${memberId}`
        try {
            await changeRegistration(() =>
                events.registerForEvent(ev.id, {eventDate: date, memberId: memberIdParam(memberId), fields}))
        } finally {
            registering.value = null
        }
    }

    /**
     * Signing up. Where the appointment asks questions, the answers have to be collected first, so
     * the request is parked in {@link fieldPrompt} for the screen to render a dialog for.
     */
    async function registerForEvent(ev: StationEvent, date: string, memberId: number) {
        const fields = await events.listRegistrationFields(ev.id).catch(() => [])
        if (fields.length > 0) {
            fieldPrompt.value = {event: ev, date, memberId, fields}
            return
        }
        await sendRegistration(ev, date, memberId)
    }

    async function confirmFieldPrompt(values: RegistrationFieldValue[]) {
        const prompt = fieldPrompt.value
        if (!prompt) return
        fieldPrompt.value = null
        await sendRegistration(prompt.event, prompt.date, prompt.memberId, values)
    }

    function cancelFieldPrompt() {
        fieldPrompt.value = null
    }

    async function declineEvent(ev: StationEvent, date: string, memberId: number) {
        await changeRegistration(() =>
            events.declineEvent(ev.id, {eventDate: date, memberId: memberIdParam(memberId)}))
    }

    async function withdrawRegistration(regId: number) {
        await changeRegistration(() => events.withdrawRegistration(regId))
    }

    return {
        registering,
        fieldPrompt,
        registerForEvent,
        declineEvent,
        withdrawRegistration,
        confirmFieldPrompt,
        cancelFieldPrompt,
    }
}
