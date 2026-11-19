/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import {WhenFull} from '@/api/generated/schema'
import FullDateDialog from './FullDateDialog.vue'

const places = {taken: 12, limit: 12, waiting: 0, full: true}

function bodyText(): string {
    return document.querySelector('[data-testid="full-date-dialog"]')?.textContent ?? ''
}

function press(testId: string) {
    document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)?.click()
}

/**
 * Letting somebody in on a full date asks first: signing up offers the waiting list or going beyond
 * the limit, accepting only going beyond it.
 */
describe('FullDateDialog', () => {
    it('offers the waiting list and going beyond the limit for a sign-up', async () => {
        const dialog = await mountSuspended(FullDateDialog, {props: {question: {kind: 'signUp', places}}, attachTo: document.body})

        expect(bodyText()).toContain('12 von 12 Plätzen belegt')
        press('full-date-queue')

        expect(dialog.emitted('answer')?.[0]).toEqual([WhenFull.QUEUE])
        dialog.unmount()
    })

    it('asks only whether to accept anyway', async () => {
        const dialog = await mountSuspended(FullDateDialog, {props: {question: {kind: 'accept', places: null}}, attachTo: document.body})

        expect(bodyText()).toContain('Trotzdem bestätigen?')
        expect(document.querySelector('[data-testid="full-date-queue"]')).toBeNull()
        press('full-date-over-limit')

        expect(dialog.emitted('answer')?.[0]).toEqual([WhenFull.OVER_LIMIT])
        dialog.unmount()
    })
})
