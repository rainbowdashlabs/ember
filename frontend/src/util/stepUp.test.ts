/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, describe, expect, it} from 'vitest'
import {requestStepUp, StepUpCancelledError, stepUpPending, useStepUpPrompt} from './stepUp'

const {current, complete, cancel} = useStepUpPrompt()

afterEach(() => {
    if (stepUpPending()) cancel()
})

describe('requestStepUp', () => {
    it('shows the prompt for the category that asked for it', async () => {
        const pending = requestStepUp('INSTANCE_CONFIG')
        expect(current.value?.category).toBe('INSTANCE_CONFIG')

        complete()
        await expect(pending).resolves.toBeUndefined()
        expect(current.value).toBeNull()
    })

    it('prompts without a category when the server named none', async () => {
        const pending = requestStepUp(null)
        expect(stepUpPending()).toBe(true)
        expect(current.value?.category).toBeNull()

        cancel()
        await expect(pending).rejects.toBeInstanceOf(StepUpCancelledError)
    })

    it('gives every waiter the same outcome instead of stacking prompts', async () => {
        const first = requestStepUp('ACCOUNT_SECURITY')
        const second = requestStepUp('ROLE_CHANGE')
        const third = requestStepUp('ACCOUNT_SECURITY')
        expect(current.value?.category).toBe('ACCOUNT_SECURITY')

        complete()
        await expect(Promise.all([first, second, third])).resolves.toEqual([undefined, undefined, undefined])
    })

    it('rejects every waiter when the reader dismisses it', async () => {
        const first = requestStepUp('FEDERATION')
        const second = requestStepUp('FEDERATION')

        cancel()
        await expect(first).rejects.toBeInstanceOf(StepUpCancelledError)
        await expect(second).rejects.toBeInstanceOf(StepUpCancelledError)
        expect(stepUpPending()).toBe(false)
    })

    /**
     * A second refusal has to be able to open a second prompt, or the action it belongs to dies
     * with no way for anybody to answer it.
     */
    it('opens again after the previous prompt was settled', async () => {
        const first = requestStepUp('INSTANCE_CONFIG')
        complete()
        await first

        const second = requestStepUp('INSTANCE_CONFIG')
        expect(stepUpPending()).toBe(true)
        complete()
        await expect(second).resolves.toBeUndefined()
    })

    it('does not settle a later prompt with an earlier outcome', async () => {
        const first = requestStepUp('INSTANCE_CONFIG')
        complete()
        await first

        const second = requestStepUp('INSTANCE_CONFIG')
        cancel()
        await expect(second).rejects.toBeInstanceOf(StepUpCancelledError)
    })
})
