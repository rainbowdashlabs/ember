/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {defineComponent, ref, type Ref} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {
    RequirementSignatureState,
    RequirementStatus,
    type AppointmentDocuments,
    type EventSummary,
    type ParticipantDocuments,
} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'
import {useEventAnswer} from './useEventAnswer'

const registerForEvent = vi.fn()
const declineEvent = vi.fn()
const withdrawRegistration = vi.fn()
const listRegistrationFields = vi.fn()

const documentsToBring = vi.fn()

vi.mock('@/api', () => ({
    events: {
        registerForEvent: (...args: unknown[]) => registerForEvent(...args),
        declineEvent: (...args: unknown[]) => declineEvent(...args),
        withdrawRegistration: (...args: unknown[]) => withdrawRegistration(...args),
        listRegistrationFields: (...args: unknown[]) => listRegistrationFields(...args),
    },
    appointmentDocuments: {
        documentsToBring: (...args: unknown[]) => documentsToBring(...args),
    },
}))

vi.mock('@/composables/useSidebarCounts', () => ({
    useSidebarCounts: () => ({refresh: () => undefined}),
}))

const date = '2026-09-04'
const appointment: EventSummary = {
    id: 4,
    stationId: '1',
    name: 'Übungsabend',
    description: null,
    eventType: 'ONE_TIME',
    dayOfWeek: null,
    startTime: '2026-09-04T17:00:00Z',
    endTime: '2026-09-04T19:00:00Z',
    requiresRegistration: true,
    registrationDeadline: null,
    categoryId: null,
    templateId: null,
    restricted: false,
    seriesCancelled: false,
    registrationLimit: null,
}

const child = {key: 11, name: 'Mira'}
const sibling = {key: 12, name: 'Jonas'}

/** The composable reaches for the locale, so it is used from inside a component as the app does. */
function answerWith(failure: Ref<Failure | null>, afterChange: () => Promise<void> = async () => undefined) {
    let api: ReturnType<typeof useEventAnswer> | null = null
    mount(defineComponent({
        setup() {
            api = useEventAnswer(ref(11), afterChange, failure)
            return () => null
        },
    }))
    return api as unknown as ReturnType<typeof useEventAnswer>
}

describe('useEventAnswer', () => {
    beforeEach(() => {
        registerForEvent.mockReset()
        declineEvent.mockReset()
        withdrawRegistration.mockReset()
        listRegistrationFields.mockReset()
        listRegistrationFields.mockResolvedValue([])
        registerForEvent.mockResolvedValue(undefined)
        declineEvent.mockResolvedValue(undefined)
        documentsToBring.mockReset()
        documentsToBring.mockResolvedValue({required: [], own: [], participants: null})
    })

    /**
     * A guardian answers for the whole household at once, and the answers go out one at a time. The
     * message about the one that was refused used to be wiped by the one that went through, which
     * left them looking at a closed dialog and two children they believed were signed up.
     */
    it('keeps the refusal of the first child on screen after the second has gone through', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValueOnce(new Error('nope'))

        await answer.registerFor(appointment, date, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(registerForEvent, 'both were asked for').toHaveBeenCalledTimes(2)
        expect(failure.value, 'and the one that was refused is still said').not.toBeNull()
    })

    it('says nothing went wrong when the whole household got through', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)

        await answer.registerFor(appointment, date, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(failure.value).toBeNull()
    })

    it('carries the same refusal out of a household that was turned down', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        declineEvent.mockRejectedValueOnce(new Error('nope'))

        await answer.declineFor(appointment, date, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(declineEvent).toHaveBeenCalledTimes(2)
        expect(failure.value).not.toBeNull()
    })

    /** The message belongs to the answer being given now, not to the one given before it. */
    it('clears what went wrong last time when the next answer is given', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValueOnce(new Error('nope'))
        await answer.registerFor(appointment, date, [child])

        expect(failure.value).not.toBeNull()

        await answer.registerFor(appointment, date, [child])

        expect(failure.value).toBeNull()
    })

    it('says so when taking an answer back is refused', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        withdrawRegistration.mockRejectedValue(new Error('nope'))

        await answer.withdrawRegistration(3)

        expect(failure.value).not.toBeNull()
    })

    /**
     * A sign-up the server took, followed by a list that would not come back, is not a sign-up that
     * failed. Saying it was sends a member who already holds a place to take a second one.
     */
    it('tells a list that would not refresh apart from an answer that was refused', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure, async () => { throw new Error('nope') })

        await answer.registerFor(appointment, date, [child])

        expect(registerForEvent, 'the place was taken').toHaveBeenCalledTimes(1)
        expect(failure.value?.message)
            .toBe('Das hat geklappt, aber die Ansicht konnte danach nicht aktualisiert werden.')
    })

    /**
     * A closed list is the appointment working as intended, so the member is pointed at whoever can
     * still help rather than invited to file a bug about it.
     */
    it('names the lead for a closed list, and offers no report', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValue({response: {status: 400, data: {}}})

        await answer.registerFor(appointment, date, [child])

        expect(failure.value?.message)
            .toBe('Die Anmeldung ist geschlossen. Wer den Termin leitet, kann dich noch auf die Liste setzen.')
        expect(failure.value?.reportable).toBe(false)
    })

    /** One person with nothing to ask is a single press, and the dialog never opens. */
    it('answers for a single person without asking anything', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)

        await answer.registerFor(appointment, date, [child])

        expect(answer.answerPrompt.value).toBeNull()
        expect(registerForEvent).toHaveBeenCalledTimes(1)
    })

    /**
     * Registering ends on the signing step where a document to bring of the person just registered still
     * waits for a signature, and nowhere else.
     */
    it('ends a registration on the signing step where a document waits for a signature', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        documentsToBring.mockResolvedValue(withCopies(
            participant(child, RequirementSignatureState.OPEN),
            participant(sibling, RequirementSignatureState.OPEN)))

        await answer.registerFor(appointment, date, [child])

        expect(documentsToBring).toHaveBeenCalledWith(appointment.id, date)
        expect(answer.signingStep.value?.copies.map(copy => copy.memberId), 'only the one just registered').toEqual([11])
        answer.closeSigningStep()
        expect(answer.signingStep.value).toBeNull()
    })

    it('ends a registration without a step where nothing waits for a signature', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        documentsToBring.mockResolvedValue(withCopies(participant(child, RequirementSignatureState.SIGNED)))

        await answer.registerFor(appointment, date, [child])

        expect(answer.signingStep.value).toBeNull()
    })

    it('offers no step for a registration that was refused', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValue(new Error('nope'))

        await answer.registerFor(appointment, date, [child])

        expect(documentsToBring).not.toHaveBeenCalled()
        expect(answer.signingStep.value).toBeNull()
    })

    it('offers the household one step for everybody whose registration landed', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        registerForEvent.mockRejectedValueOnce(new Error('nope'))
        documentsToBring.mockResolvedValue(withCopies(
            participant(child, RequirementSignatureState.OPEN),
            participant(sibling, RequirementSignatureState.OPEN)))

        await answer.registerFor(appointment, date, [child, sibling])
        await answer.confirmAnswerPrompt([{key: child.key, fields: []}, {key: sibling.key, fields: []}])

        expect(documentsToBring).toHaveBeenCalledTimes(1)
        expect(answer.signingStep.value?.copies.map(copy => copy.memberId)).toEqual([12])
    })

    it('lets the registration stand without a step where the documents cannot be read', async () => {
        const failure = ref<Failure | null>(null)
        const answer = answerWith(failure)
        documentsToBring.mockRejectedValue(new Error('nope'))

        await answer.registerFor(appointment, date, [child])

        expect(answer.signingStep.value).toBeNull()
        expect(failure.value).toBeNull()
    })
})

function participant(person: {key: number; name: string}, state: RequirementSignatureState): ParticipantDocuments {
    return {
        memberId: person.key,
        name: person.name,
        documents: [{
            templateId: 8,
            name: 'Einverständnis',
            status: RequirementStatus.GENERATED,
            documentId: 40 + person.key,
            generatedAt: '2026-09-01T10:00:00Z',
            outdated: false,
            paper: null,
            signature: {
                templateId: 8,
                memberId: person.key,
                requestUid: `0b9f5c1e-8f6d-4a39-9d55-2c1b7f3d4e${person.key}`,
                state,
                fields: [{id: 70 + person.key, name: 'participant', signerName: person.name, state, yours: true}],
                withdrawable: false,
                withdrawnAt: null,
            },
            agreementOffered: false,
        }],
    }
}

function withCopies(...own: ParticipantDocuments[]): AppointmentDocuments {
    return {required: [], own, participants: null}
}
