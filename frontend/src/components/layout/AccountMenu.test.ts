/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mount} from '@vue/test-utils'
import {createMemoryHistory, createRouter} from 'vue-router'
import AccountMenu from './AccountMenu.vue'

/** A real router, because the menu closes itself whenever the page underneath it changes. */
const router = createRouter({
    history: createMemoryHistory(),
    routes: [{path: '/:rest(.*)*', component: {template: '<div/>'}}],
})

/**
 * The account menu, which is a dropdown on a wide screen and a drawer on a narrow one.
 *
 * <p>The drawer is the interesting half: it lays a dark sheet over the whole page, and a sheet that
 * swallows the press without closing anything leaves the reader tapping at a page that will not
 * answer.
 */
function mountMenu(mode: 'dropdown' | 'drawer') {
    return mount(AccountMenu, {
        props: {mode, open: true},
        global: {plugins: [router], stubs: {DropdownMenuItem: true, transition: false}},
    })
}

describe('AccountMenu', () => {
    it('closes the drawer when the sheet beside it is pressed', async () => {
        const wrapper = mountMenu('drawer')

        await wrapper.get('[data-testid="account-drawer-backdrop"]').trigger('click')

        expect(wrapper.emitted('close')).toHaveLength(1)
    })

    it('keeps the drawer open while the panel itself is pressed', async () => {
        const wrapper = mountMenu('drawer')

        await wrapper.get('.w-72').trigger('click')

        expect(wrapper.emitted('close')).toBeUndefined()
    })
})
