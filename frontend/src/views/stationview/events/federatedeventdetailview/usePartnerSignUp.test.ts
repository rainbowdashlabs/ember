/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {ref} from 'vue'
import {usePartnerSignUp} from './usePartnerSignUp'
import type {EventRegistrationField} from '@/api/generated/schema'

const SIZE = {id: 5, name: 'Größe', fieldType: 'TEXT', config: {required: true}} as unknown as EventRegistrationField

/** A partner's appointment asks its questions before the sign-up goes out, and sends the answers with it. */
describe('usePartnerSignUp', () => {
    it('signs up at once where the appointment asks nothing', async () => {
        const register = vi.fn(async () => undefined)
        const signUp = usePartnerSignUp(ref([]), register)

        await signUp.start('me')

        expect(register).toHaveBeenCalledWith('me', [])
        expect(signUp.open.value).toBe(false)
    })

    it('asks the questions first and sends the answers with the sign-up', async () => {
        const register = vi.fn(async () => undefined)
        const signUp = usePartnerSignUp(ref([SIZE]), register)

        await signUp.start('ward')
        expect(signUp.open.value).toBe(true)
        expect(register).not.toHaveBeenCalled()

        await signUp.answer([{fieldId: 5, value: 'L'}])

        expect(register).toHaveBeenCalledWith('ward', [{fieldId: 5, value: 'L'}])
        expect(signUp.open.value).toBe(false)
    })

    it('sends nothing once the questions are closed', async () => {
        const register = vi.fn(async () => undefined)
        const signUp = usePartnerSignUp(ref([SIZE]), register)

        await signUp.start('me')
        signUp.open.value = false
        await signUp.answer([{fieldId: 5, value: 'L'}])

        expect(register).not.toHaveBeenCalled()
    })

    it('keeps the questions open where the sign-up is refused', async () => {
        const register = vi.fn(async () => {
            throw new Error('refused')
        })
        const signUp = usePartnerSignUp(ref([SIZE]), register)

        await signUp.start('me')
        await signUp.answer([{fieldId: 5, value: ''}])

        expect(signUp.open.value).toBe(true)
    })
})
