/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import TagsHelp from './TagsHelp.vue'
import GroupsHelp from './GroupsHelp.vue'
import ChangesHelp from './ChangesHelp.vue'

describe('member help articles', () => {
    it('show the real tag panels with a tag selected', async () => {
        const article = await mountSuspended(TagsHelp)

        expect(article.text()).toContain('Tag erstellen')
        expect(article.text()).toContain('Mitglieder hinzufügen')
    })

    it('show the real group panels with a group selected', async () => {
        const article = await mountSuspended(GroupsHelp)

        expect(article.text()).toContain('Neue Gruppe')
        expect(article.text()).toContain('Mitglied hinzufügen')
        expect(article.text()).toContain('Neues Gruppenset')
    })

    it('show the real pending changes card with both demo changes', async () => {
        const article = await mountSuspended(ChangesHelp)

        expect(article.text()).toContain('0170 2222222')
        expect(article.text()).toContain('M')
    })
})
