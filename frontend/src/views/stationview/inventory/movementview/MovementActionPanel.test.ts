/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import MovementActionPanel from './MovementActionPanel.vue'
import type {MovementStep} from '@/api/movements'

/**
 * What the panel asks the picker for when a step wants to know which piece arrived.
 *
 * <p>The filters live in the picker and the answers live in the movement, so this is about the wire
 * between them: a panel that knows whose gear it is about and does not say so leaves the picker
 * offering every piece the station has, which is how somebody else's jacket gets handed over.
 */

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}},
})

function namingStep(): MovementStep {
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
    } as MovementStep
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

    /** The button says what pressing it makes true, which is the step's own words. */
    it('names the step on the button that answers it', () => {
        const wrapper = panel()

        expect(wrapper.find('[data-testid="movement-acknowledge"]').text()).toBe('Ersatz bereit')
    })
})
