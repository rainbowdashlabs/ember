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
import type {AnswerablePerson} from '@/util/eventAnswers'

/** Everybody an answer can be given for, as the screens hold them. */
type AnswerablePeople = AnswerablePerson[]

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
     * An answer waiting to be given, once there is something to decide about it: which of several
     * people it is for, or what the appointment's questions are answered with. Cleared once the
     * dialog the screen renders for it is confirmed or dismissed.
     */
    const answerPrompt = ref<{
        event: StationEvent
        date: string
        people: AnswerablePerson[]
        fields: EventRegistrationField[]
        attending: boolean
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
     * Signing up, for one person or for a household.
     *
     * <p>The dialog opens where there is something to decide: which of several people are coming, or
     * what the appointment's questions are answered with. One person and no questions is a single
     * press, because putting a dialog in front of the commonest answer of all only slows it down.
     */
    async function registerFor(ev: StationEvent, date: string, people: AnswerablePeople) {
        if (people.length === 0) return
        const fields = await events.listRegistrationFields(ev.id).catch(() => [])
        if (people.length === 1 && fields.length === 0) {
            await sendRegistration(ev, date, people[0]!.key)
            return
        }
        answerPrompt.value = {event: ev, date, people, fields, attending: true}
    }

    /**
     * Refusing, for one person or for a household.
     *
     * <p>A refusal asks nothing, so a single person goes straight through: the screen has already
     * asked whether they are sure. Several open the dialog, where ticking the ones who are not coming
     * is the confirmation.
     */
    async function declineFor(ev: StationEvent, date: string, people: AnswerablePeople) {
        if (people.length === 0) return
        if (people.length === 1) {
            await sendDecline(ev, date, people[0]!.key)
            return
        }
        answerPrompt.value = {event: ev, date, people, fields: [], attending: false}
    }

    /** Gives the parked answer for everybody it was confirmed for, one request each. */
    async function confirmAnswerPrompt(answers: { key: number; fields: RegistrationFieldValue[] }[]) {
        const prompt = answerPrompt.value
        if (!prompt) return
        answerPrompt.value = null
        for (const answer of answers) {
            if (prompt.attending) {
                await sendRegistration(prompt.event, prompt.date, answer.key, answer.fields)
            } else {
                await sendDecline(prompt.event, prompt.date, answer.key)
            }
        }
    }

    function cancelAnswerPrompt() {
        answerPrompt.value = null
    }

    async function sendDecline(ev: StationEvent, date: string, memberId: number) {
        await changeRegistration(() =>
            events.declineEvent(ev.id, {eventDate: date, memberId: memberIdParam(memberId)}))
    }

    async function withdrawRegistration(regId: number) {
        await changeRegistration(() => events.withdrawRegistration(regId))
    }

    return {
        registering,
        answerPrompt,
        registerFor,
        declineFor,
        withdrawRegistration,
        confirmAnswerPrompt,
        cancelAnswerPrompt,
    }
}
