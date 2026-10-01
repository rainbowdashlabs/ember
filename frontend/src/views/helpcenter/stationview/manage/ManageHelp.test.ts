/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import MembersConfigHelp from './MembersConfigHelp.vue'
import ModulesHelp from './ModulesHelp.vue'

describe('station management help articles', () => {
    it('show the real question and audience panels with the demo questions', async () => {
        const article = await mountSuspended(MembersConfigHelp)

        expect(article.text()).toContain('Kleidergröße')
        expect(article.text()).toContain('Geburtsdatum')
    })

    it('show the real module list without anything locked', async () => {
        const article = await mountSuspended(ModulesHelp)

        expect(article.text()).toContain('Aktiviere oder deaktiviere Module')
        expect(article.text()).not.toContain('Vom Verband')
    })
})
