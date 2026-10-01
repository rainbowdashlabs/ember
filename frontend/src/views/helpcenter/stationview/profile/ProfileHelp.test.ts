/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import SessionsHelp from './SessionsHelp.vue'
import NotificationsHelp from './NotificationsHelp.vue'
import SettingsHelp from './SettingsHelp.vue'

describe('profile help articles', () => {
    it('show the real session list with the demo sessions', async () => {
        const article = await mountSuspended(SessionsHelp)

        expect(article.text()).toContain('Hamburg')
        expect(article.text()).toContain('Firefox')
    })

    it.each([NotificationsHelp, SettingsHelp])('show the real notification settings with the demo provider', async article => {
        const mounted = await mountSuspended(article)

        expect(mounted.text()).toContain('Beispiel Mail GmbH')
    })
})
