/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended, registerEndpoint} from '@nuxt/test-utils/runtime'
import {createError, defineEventHandler} from 'h3'
import SharedPageView from './SharedPageView.vue'

/**
 * A link that no longer opens a page is the ordinary end of a shared link: it was replaced, the page
 * was taken back, or the station closed its pages. The reader holds nothing but the link, so the page
 * has to say that in words they can act on rather than report a fault nobody can name.
 */
describe('SharedPageView', () => {
    it('says the link leads nowhere when the server knows no page behind it', async () => {
        registerEndpoint('/api/v1/public/shared/zurueckgezogen', defineEventHandler(() => {
            throw createError({
                statusCode: 404,
                statusMessage: 'Not Found',
                data: {error: 'Not Found', message: 'No page is reached by this link', code: 'P-011'},
            })
        }))

        const wrapper = await mountSuspended(SharedPageView, {route: '/s/zurueckgezogen'})

        expect(wrapper.text()).toContain('Dieser Link führt nirgendwo mehr hin.')
        expect(wrapper.text()).not.toContain('Ember kann nicht sagen was')
    })
})
