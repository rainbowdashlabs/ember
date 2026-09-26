/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {events} from '@/api'
import {apiErrorStatus} from '@/util/apiError'
import {showToast} from '@/util/toast'
import type {EventRegistrationField, RegistrationFieldValue, StationEvent} from '@/api/events'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {describeFailure, type Failure} from '@/util/failure'
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
 * @param failure         the screen's failure channel, written to rather than thrown at
 */
export function useEventAnswer(
    currentMemberId: Ref<number>,
    afterChange: () => Promise<void>,
    failure: Ref<Failure | null>,
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

    /**
     * Opens a gesture the reader has just made. The screen's failure belongs to that gesture and not to
     * a single request inside it: answering for a household is one gesture and several requests, and
     * what went wrong for the first person has to still be on screen once the last has gone through.
     */
    function beginAnswer() {
        failure.value = null
    }

    /**
     * Why an answer was refused, in the terms the member can act on.
     *
     * <p>A refusal the member can do something about is told apart from a failure they cannot. The
     * server answers a closed list with a plain refusal, and saying only that something went wrong
     * leaves somebody pressing the same button again: the caller passes the words for that case, and
     * they name whoever can still help. Those words win, and everything around them, what sort of
     * failure it was and what to do next, still comes from the failure itself. It is marked as
     * nothing to report, because a list that closed on time is the product working as intended and a
     * bug filed against it buries the real ones.
     */
    function refusalFor(e: unknown, refusedMessage?: string): Failure {
        const described = describeFailure(e, t)
        if (refusedMessage && apiErrorStatus(e) === 400) {
            return {...described, message: refusedMessage, reportable: false}
        }
        return described
    }

    /**
     * Carries out an answer, and only then reloads what the screen shows.
     *
     * <p>The two are answered for separately. An answer the server had already taken, followed by a
     * list that would not come back, used to say the answer had been refused, and a member told that
     * presses the same button again. An earlier refusal in the same gesture is left standing, because
     * a guardian answering for three children has to see the one that did not land.
     */
    async function changeRegistration(action: () => Promise<unknown>, refusedMessage?: string) {
        try {
            await action()
        } catch (e) {
            failure.value = refusalFor(e, refusedMessage)
            return
        }

        try {
            await afterChange()
            refreshSidebarCounts()
        } catch (e) {
            if (failure.value) return
            failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
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
            await changeRegistration(
                () => events.registerForEvent(ev.id, {eventDate: date, memberId: memberIdParam(memberId), fields}),
                t('eventsUpcoming.registrationClosedAskLead'))
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
        beginAnswer()
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
        beginAnswer()
        if (people.length === 1) {
            await sendDecline(ev, date, people[0]!.key)
            return
        }
        answerPrompt.value = {event: ev, date, people, fields: [], attending: false}
    }

    /**
     * Gives the parked answer for everybody it was confirmed for, one request each.
     *
     * <p>All of them together are one gesture, so the first refusal is still readable after the rest
     * have gone through: a guardian answering for two children is told when only one of them landed.
     */
    async function confirmAnswerPrompt(answers: { key: number; fields: RegistrationFieldValue[] }[]) {
        const prompt = answerPrompt.value
        if (!prompt) return
        answerPrompt.value = null
        beginAnswer()
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

    /**
     * Giving up a place, and offering it back for as long as the server would take it back.
     *
     * <p>The offer rides a toast rather than the row it came from: by the time somebody realises they
     * pressed the wrong one they have often scrolled past it, and the row may not be on the page at
     * all any more. How long it stands is the server's to say, so the toast lasts exactly as long as
     * the answer it was given.
     */
    async function withdrawRegistration(regId: number) {
        beginAnswer()
        let undoUntil: string | null = null
        await changeRegistration(async () => {
            undoUntil = (await events.withdrawRegistration(regId)).undoUntil
        })
        if (undoUntil === null) return
        const remaining = new Date(undoUntil).getTime() - Date.now()
        if (remaining <= 0) return
        showToast(t('eventsUpcoming.signedOff'), 'info', remaining, {
            label: t('eventsUpcoming.undoSignOff'),
            run: () => undoWithdrawal(regId),
        })
    }

    /**
     * Putting a withdrawal back. Past the window the server refuses, and the member is told the place
     * is not theirs to take back any more rather than left wondering whether the press landed.
     */
    async function undoWithdrawal(regId: number) {
        beginAnswer()
        await changeRegistration(() => events.undoWithdrawal(regId), t('eventsUpcoming.undoTooLate'))
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
