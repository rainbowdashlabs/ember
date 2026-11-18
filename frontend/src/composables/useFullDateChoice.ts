/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import type {WhenFull} from '@/api/generated/schema'
import {apiErrorCode} from '@/util/apiError'
import type {Places} from '@/util/eventPlaces'

/** The refusal the server answers with when somebody is let in on a date whose places are all taken. */
export const REGISTRATION_FULL_CODE = 'E-226'

/**
 * What whoever runs an appointment is asked once a date turns out to be full.
 *
 * <p>Signing somebody else up, or saying they come after all, offers both ways out: beyond the limit,
 * or onto the waiting list. Accepting a sign-up only asks whether to go beyond the limit, since the
 * sign-up already is where it would wait.
 */
export interface FullDateQuestion {
    kind: 'signUp' | 'accept'
    /** How the places stand, where the screen knows. */
    places: Places | null
}

/** Whether a failure is the server saying every place of the date is taken. */
export function isFullRefusal(e: unknown): boolean {
    return apiErrorCode(e) === REGISTRATION_FULL_CODE
}

/**
 * Asking what to do with a full date, and sending the request again with the answer.
 *
 * <p>The server is the one that knows a date is full, at the moment the request arrives, so the
 * question is asked after its refusal rather than guessed beforehand. The screen renders the
 * question while `question` holds one, and answers it with {@link answer}.
 */
export function useFullDateChoice() {
    const question = ref<FullDateQuestion | null>(null)
    let settle: ((choice: WhenFull | null) => void) | null = null

    /** Opens the question and waits for the answer, null where it was dismissed. */
    function ask(asked: FullDateQuestion): Promise<WhenFull | null> {
        settle?.(null)
        question.value = asked
        return new Promise(resolve => {
            settle = resolve
        })
    }

    /** Gives the answer to the open question, or null to leave it. */
    function answer(choice: WhenFull | null) {
        question.value = null
        const resolve = settle
        settle = null
        resolve?.(choice)
    }

    /**
     * Sends a request, and where the date turns out to be full, asks and sends it once more with the
     * answer. Any other failure, and a full date that was not answered, is the caller's to handle.
     *
     * @param kind  what is being done, which decides what is offered
     * @param places how the places stand, for the question to name
     * @param send  the request, told the choice on the second try
     * @return whether the request went through
     */
    async function sendAsking(
        kind: FullDateQuestion['kind'],
        places: Places | null,
        send: (choice?: WhenFull) => Promise<unknown>,
    ): Promise<boolean> {
        try {
            await send()
            return true
        } catch (e) {
            if (!isFullRefusal(e)) throw e
        }
        const choice = await ask({kind, places})
        if (choice === null) return false
        await send(choice)
        return true
    }

    return {question, ask, answer, sendAsking}
}
