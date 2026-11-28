/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it, vi} from 'vitest'
import {mount, flushPromises} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import MovementActionPanel from './MovementActionPanel.vue'
import ItemSearchPicker from '@/components/input/search/ItemSearchPicker.vue'
import type {MovementStepResponse} from '@/api/generated/schema'

const held = {inventoryRead: true}

vi.mock('@/composables/useSession', () => ({
    useSession: () => ({hasPermission: () => held.inventoryRead, isModuleEnabled: () => true}),
}))

/**
 * What the panel asks the picker for when a step wants to know which piece arrived.
 *
 * <p>The filters live in the picker and the answers live in the movement, so this is about the wire
 * between them: a panel that knows whose gear it is about and does not pass that on leaves the picker
 * offering every piece the station has, which is how somebody else's jacket gets handed over.
 */

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}, en: {}},
})

function namingStep(): MovementStepResponse {
    return {
        id: 7,
        position: 2,
        label: 'Ersatz bereit',
        actor: 'STATION',
        subject: 'INCOMING',
        custodyAfter: 'AT_STATION',
        picksItem: true,
        archived: false,
        current: true,
        actionable: true,
    } as MovementStepResponse
}

function panel(extra: Record<string, unknown> = {}) {
    return mount(MovementActionPanel, {
        global: {
            plugins: [i18n],
            stubs: {'font-awesome-icon': true, ItemSearchPicker: true, ScanButton: true},
        },
        props: {step: namingStep(), canForce: false, mayRecord: false, sizes: [], busy: false, ...extra},
    })
}

describe('MovementActionPanel', () => {
    it('offers only free pieces off the movement\'s own shelf, of the same owner', () => {
        const picker = panel({
            inventoryId: 3,
            inventoryType: 'EXTERNAL',
            ownerClusterId: 'body-1',
            ownerKind: 'CLUSTER',
        }).findComponent({name: 'ItemSearchPicker'})

        expect(picker.props('inventoryId'), 'a shirt is replaced by a shirt').toBe(3)
        expect(picker.props('ownerKind'), 'the association\'s gear only').toBe('CLUSTER')
        expect(picker.props('ownerClusterId'), 'and that association\'s, not another\'s').toBe('body-1')
        expect(picker.props('inventoryType'), 'off the sort of shelf this movement is about').toBe('EXTERNAL')
        expect(picker.props('excludeAssigned'), 'nothing somebody is already holding').toBe(true)
        expect(picker.props('excludeLost'), 'nothing that is missing').toBe(true)
        expect(picker.props('excludeSpokenFor'), 'nothing another movement has promised').toBe(true)
    })

    /** The button shows what pressing it makes true, which is the step's own words. */
    it('names the step on the button that answers it', () => {
        const wrapper = panel()

        expect(wrapper.find('[data-testid="movement-acknowledge"]').text()).toBe('Ersatz bereit')
    })

    /**
     * The picker asks for every piece the station keeps and everybody it has, so somebody who may
     * not read the shelf got four refusals and a form they could not fill in.
     */
    it('draws no picker for somebody who may not read the shelf', () => {
        held.inventoryRead = false
        try {
            const wrapper = panel()

            expect(wrapper.findComponent({name: 'ItemSearchPicker'}).exists()).toBe(false)
        } finally {
            held.inventoryRead = true
        }
    })

    /**
     * A planned hand-out names its piece when it is planned. The step that hands it over starts out with
     * that piece chosen and answers with it, rather than asking for one the picker would not offer.
     */
    it('starts with the piece the movement already names, and keeps it on offer', async () => {
        const wrapper = panel({promisedItemId: 57, promisedItemName: 'Sporttasche'})
        const picker = wrapper.findComponent({name: 'ItemSearchPicker'})

        expect(picker.props('modelValue'), 'the promised piece is chosen').toBe(57)
        expect(picker.props('keepItemId'), 'and offered although it is spoken for').toBe(57)
        expect(wrapper.find('[data-testid="movement-promised-item"]').exists()).toBe(true)

        const button = wrapper.find('[data-testid="movement-acknowledge"]')
        expect(button.attributes('disabled'), 'nothing is missing').toBeUndefined()
        await button.trigger('click')
        expect(wrapper.emitted('acknowledge')?.[0]?.[0]).toMatchObject({pickedItemId: 57})
    })

    /** Without a piece named, the step still waits for one. */
    it('waits for a piece where the movement names none', () => {
        const wrapper = panel()

        expect(wrapper.find('[data-testid="movement-acknowledge"]').attributes('disabled')).toBeDefined()
        expect(wrapper.find('[data-testid="movement-promised-item"]').exists()).toBe(false)
    })

    /** Somebody who may not read the shelf may still hand over the piece the movement names. */
    it('lets somebody who may not pick hand over the promised piece', () => {
        held.inventoryRead = false
        try {
            const wrapper = panel({promisedItemId: 57})

            expect(wrapper.find('[data-testid="movement-acknowledge"]').attributes('disabled')).toBeUndefined()
        } finally {
            held.inventoryRead = true
        }
    })

    /**
     * Calling off asks about the piece the member handed in. At the station it goes back unless kept;
     * once it has left, a way back is offered and not assumed.
     */
    describe('calling off', () => {
        function callingOff(oldPiece: string, forMember = true) {
            return panel({oldPiece, forMember})
        }

        it('gives a piece at the station back unless it is kept', async () => {
            const wrapper = callingOff('AT_STATION')

            expect(wrapper.find('[data-testid="call-off-give-back"]').exists()).toBe(true)
            await wrapper.get('[data-testid="movement-cancel"]').trigger('click')
            expect(wrapper.emitted('cancel')?.[0]?.[1]).toEqual({giveBack: true, planWayBack: false})

            await wrapper.get('[data-testid="call-off-give-back"]').trigger('click')
            await wrapper.get('[data-testid="movement-cancel"]').trigger('click')
            expect(wrapper.emitted('cancel')?.[1]?.[1]).toEqual({giveBack: false, planWayBack: false})
        })

        it('offers the way back for a piece that has left', async () => {
            const wrapper = callingOff('AWAY')

            expect(wrapper.find('[data-testid="call-off-give-back"]').exists()).toBe(false)
            await wrapper.get('[data-testid="call-off-way-back"]').trigger('click')
            await wrapper.get('[data-testid="movement-cancel"]').trigger('click')
            expect(wrapper.emitted('cancel')?.[0]?.[1]).toMatchObject({planWayBack: true})
        })

        it('asks nothing while the member holds it, or where the movement is for nobody', () => {
            expect(callingOff('WITH_MEMBER').find('[data-testid="call-off-choice"]').exists()).toBe(false)
            expect(callingOff('AT_STATION', false).find('[data-testid="call-off-choice"]').exists()).toBe(false)
        })
    })

    describe('ordering the replacement of a swap', () => {
        const swap = {movementId: 5, purpose: 'EXCHANGE', ownerKind: 'STATION'}

        async function counted(wrapper: ReturnType<typeof panel>, fits: number) {
            wrapper.findComponent(ItemSearchPicker).vm.$emit('fits', fits)
            await flushPromises()
        }

        it('offers an order when the shelf has nothing in the size asked for', async () => {
            const wrapper = panel(swap)
            await counted(wrapper, 0)
            expect(wrapper.find('[data-testid="swap-order-place"]').exists()).toBe(true)
        })

        it('offers none while a fitting piece is on the shelf, or for the association\'s gear', async () => {
            const stocked = panel(swap)
            await counted(stocked, 2)
            expect(stocked.find('[data-testid="swap-order-place"]').exists()).toBe(false)

            const theirs = panel({...swap, ownerKind: 'CLUSTER'})
            await counted(theirs, 0)
            expect(theirs.find('[data-testid="swap-order"]').exists()).toBe(false)
        })

        it('waits for the order instead of picking a piece', () => {
            const wrapper = panel({...swap, orderId: 9, mayRecord: true})
            expect(wrapper.find('[data-testid="swap-order-waiting"]').exists()).toBe(true)
            expect(wrapper.findComponent(ItemSearchPicker).exists()).toBe(false)
            expect(wrapper.find('[data-testid="movement-record-arrival"]').exists()).toBe(false)
            expect(wrapper.get('[data-testid="movement-acknowledge"]').attributes('disabled')).toBeDefined()
        })
    })
})
