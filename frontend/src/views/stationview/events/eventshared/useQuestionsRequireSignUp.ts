/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, watch, type Ref} from 'vue'

/**
 * Questions are answered when signing up, so an appointment or template that asks any needs sign-up.
 *
 * <p>The questions stay on the form whether sign-up is on or not, so they are found where they are
 * looked for. Adding the first one switches sign-up on, and while any is asked, sign-up cannot be
 * switched off: the form says why instead of hiding the questions again. Sign-up switched off from
 * elsewhere while questions are asked, such as by a template applied to the form, is switched back on.
 *
 * @param asksQuestions        whether any question is asked: on the form, or by the template a batch is made from
 * @param requiresRegistration whether the form asks for sign-up
 * @return whether sign-up is held on by the questions
 */
export function useQuestionsRequireSignUp(asksQuestions: () => boolean, requiresRegistration: Ref<boolean>) {
    const signUpHeld = computed(asksQuestions)

    watch([signUpHeld, requiresRegistration], ([held, on]) => {
        if (held && !on) requiresRegistration.value = true
    }, {immediate: true})

    return {signUpHeld}
}
