/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {nextTick, ref} from 'vue'
import {useQuestionsRequireSignUp} from './useQuestionsRequireSignUp'

/** Questions are answered when signing up, so while any is asked, sign-up stays on. */
describe('useQuestionsRequireSignUp', () => {
    it('switches sign-up on with the first question', async () => {
        const questions = ref<string[]>([])
        const signUp = ref(false)
        const {signUpHeld} = useQuestionsRequireSignUp(() => questions.value.length > 0, signUp)
        expect(signUpHeld.value).toBe(false)

        questions.value = ['Größe']
        await nextTick()

        expect(signUpHeld.value).toBe(true)
        expect(signUp.value).toBe(true)
    })

    it('switches sign-up back on when something else switches it off while questions are asked', async () => {
        const signUp = ref(true)
        useQuestionsRequireSignUp(() => true, signUp)

        signUp.value = false
        await nextTick()

        expect(signUp.value).toBe(true)
    })
})
