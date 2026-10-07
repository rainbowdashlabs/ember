/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {nextTick} from 'vue'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import type {IssuerChoice, PreviewIssuer} from '@/api/generated/schema'
import IssuerFields from './IssuerFields.vue'
import IssuerOverride from './IssuerOverride.vue'

const WARDEN: PreviewIssuer = {memberId: 5, name: 'Erika Wehr', function: 'Jugendwartin', fixed: true}

async function mounted(templateIssuer: PreviewIssuer = WARDEN) {
    const choices: (IssuerChoice | null)[] = []
    const override = await mountSuspended(IssuerOverride, {
        props: {
            modelValue: null,
            templateIssuer,
            members: [{value: '7', name: 'Max Weiß'}],
            'onUpdate:modelValue': (choice: IssuerChoice | null) => choices.push(choice),
        },
    })
    return {override, choices}
}

/**
 * A manager sees who the template names as its issuer and may pick somebody else for this one document;
 * the pick counts only once a member is chosen.
 */
describe('IssuerOverride', () => {
    it('names the template\'s issuer with their function', async () => {
        const {override} = await mounted()

        expect(override.text()).toContain('Laut Vorlage: Erika Wehr, Jugendwartin')
        expect(override.findComponent(IssuerFields).exists()).toBe(false)
    })

    it('says so where the template names nobody', async () => {
        const {override} = await mounted({memberId: null, name: null, function: null, fixed: true})

        expect(override.text()).toContain('Laut Vorlage: niemand gewählt')
    })

    it('hands on another member once one is picked, with the template\'s function to start from', async () => {
        const {override, choices} = await mounted()

        override.findComponent(ToggleSetting).vm.$emit('update:modelValue', true)
        await nextTick()
        const fields = override.findComponent(IssuerFields)
        expect(fields.props('issuerFunction')).toBe('Jugendwartin')
        expect(choices).toEqual([])

        fields.vm.$emit('update:memberId', 7)
        fields.vm.$emit('update:issuerFunction', ' Kassenwart ')
        await nextTick()
        expect(choices.at(-1)).toEqual({memberId: 7, function: 'Kassenwart'})

        override.findComponent(ToggleSetting).vm.$emit('update:modelValue', false)
        await nextTick()
        expect(choices.at(-1)).toBeNull()
    })
})
