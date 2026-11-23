/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import MovementPieceBadge from './MovementPieceBadge.vue'

const RouterLink = {template: '<a :data-to="JSON.stringify(to)"><slot/></a>', props: ['to']}

function badge(movementId: number | null) {
    return mount(MovementPieceBadge, {props: {movementId}, global: {stubs: {'router-link': RouterLink}}})
}

describe('MovementPieceBadge', () => {
    it('names the movement carrying the piece and leads to it', () => {
        const link = badge(78).get('[data-testid="movement-piece-badge"]')

        expect(link.text()).toContain('78')
        expect(JSON.parse(link.attributes('data-to') ?? '{}')).toMatchObject({
            name: 'inventory-movement-detail',
            params: {id: '78'},
        })
    })

    it('draws nothing for a piece no movement carries', () => {
        expect(badge(null).find('[data-testid="movement-piece-badge"]').exists()).toBe(false)
    })
})
