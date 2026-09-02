/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {ref} from 'vue'
import {createI18n} from 'vue-i18n'
import LendingSharePanel from './LendingSharePanel.vue'
import {ShareGrant, ShareScope} from '@/api/lending'
import de from '@/i18n/de-DE'

const getShare = vi.fn()

vi.mock('@/api/lending', async (importOriginal) => ({
    ...(await importOriginal<typeof import('@/api/lending')>()),
    getShare: (...args: unknown[]) => getShare(...args),
}))

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({loaded: ref(true), hasPermission: () => true}),
}))

vi.mock('@/composables/useInventoryRoutes', () => ({
    useInventoryRoutes: () => ({lendingShares: 'inventory-lending-shares'}),
}))

/**
 * Where the sharing card stands, and where it says nothing.
 *
 * <p>Gear a station does not own cannot be lent by it, so an offer written on such gear could never
 * be filled. The card is left off those screens rather than shown and refused on save.
 */
describe('LendingSharePanel', () => {
    const i18n = createI18n({legacy: false, locale: 'de-DE', messages: {'de-DE': de}})

    function panel(lendable: boolean) {
        return mount(LendingSharePanel, {
            props: {target: 'inventory' as const, targetId: 7, targetName: 'Funkgeräte', lendable},
            global: {plugins: [i18n], stubs: {'font-awesome-icon': true, LendingShareModal: true}},
        })
    }

    async function settled(card: ReturnType<typeof panel>) {
        await Promise.resolve()
        await Promise.resolve()
        await card.vm.$nextTick()
        return card
    }

    beforeEach(() => {
        vi.clearAllMocks()
        getShare.mockResolvedValue({shared: false, grant: null, scope: null, partnerIds: []})
    })

    it('offers the decision on gear the station may lend', async () => {
        const card = await settled(panel(true))

        expect(card.find('[data-testid="lending-share-panel"]').exists()).toBe(true)
        expect(card.find('[data-testid="lending-share-edit"]').exists()).toBe(true)
    })

    it('says nothing at all where the gear is not the station to lend', async () => {
        const card = await settled(panel(false))

        expect(card.find('[data-testid="lending-share-panel"]').exists()).toBe(false)
        expect(getShare).not.toHaveBeenCalled()
    })

    it('names what is currently offered so the state is read without opening it', async () => {
        getShare.mockResolvedValue({
            shared: true,
            grant: ShareGrant.GRANT,
            scope: ShareScope.ALL_PARTNERS,
            partnerIds: [],
        })

        const card = await settled(panel(true))

        expect(card.find('[data-testid="lending-share-state"]').text())
            .toBe(de.lendingShare.stateAllPartners)
    })
})
