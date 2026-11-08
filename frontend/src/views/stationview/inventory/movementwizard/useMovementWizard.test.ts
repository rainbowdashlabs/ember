/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {MovementPurpose} from '@/api/generated/schema'
import {useMovementWizard, type WizardPrefill} from './useMovementWizard'

vi.mock('@/api', () => ({
    inventory: {listSizes: vi.fn(async () => []), listInventories: vi.fn(async () => [])},
    movements: {resolveFlow: vi.fn(async () => null), createMovement: vi.fn(), acknowledgeStep: vi.fn()},
}))

/**
 * A swap asks for the size of the piece handed in unless the reader picks another, whether the piece is
 * named by the screen that opened the wizard or picked in it.
 */
describe('useMovementWizard', () => {
    it('asks a prefilled swap for the size of the piece handed in', async () => {
        const wizard = useMovementWizard(() => ({
            purpose: MovementPurpose.EXCHANGE, itemId: 4, inventoryId: 2, oldSizeId: 7, skip: ['purpose', 'party', 'subject'],
        }))

        await wizard.start()

        expect(wizard.newSizeId.value).toBe(7)
    })

    it('keeps a size the screen that opened it asked for', async () => {
        const wizard = useMovementWizard(() => ({purpose: MovementPurpose.EXCHANGE, oldSizeId: 7, newSizeId: 8}))

        await wizard.start()

        expect(wizard.newSizeId.value).toBe(8)
    })

    it('takes the size of the piece picked for a swap when leaving that question', async () => {
        const prefill: WizardPrefill = {purpose: MovementPurpose.EXCHANGE, memberId: 3, skip: ['purpose', 'party']}
        const wizard = useMovementWizard(() => prefill)
        await wizard.start()

        wizard.itemId.value = 4
        wizard.inventoryId.value = 2
        wizard.oldSizeId.value = 9
        await wizard.next()

        expect(wizard.step.value).toBe('reason')
        expect(wizard.newSizeId.value).toBe(9)
    })

    it('offers a request whether or not the Träger uses Ember', () => {
        const wizard = useMovementWizard(() => ({}))

        expect(wizard.purposes.value).toContain(MovementPurpose.REQUEST)
    })

    it('leaves the size open on an issue', async () => {
        const wizard = useMovementWizard(() => ({purpose: MovementPurpose.ISSUE, oldSizeId: 7}))

        await wizard.start()

        expect(wizard.newSizeId.value).toBeNull()
    })
})
