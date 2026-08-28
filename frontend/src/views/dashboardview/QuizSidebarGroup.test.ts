/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import QuizSidebarGroup from './QuizSidebarGroup.vue'
import {sessionInfo} from '@/util/sessionState'
import type {SessionInfo} from '@/api/types'

/**
 * What the group is called, and what it holds, depends on the reader.
 *
 * <p>A member who may neither write a test sheet nor sit one was shown a group called
 * "Quiz &amp; Prüfungen" with a "Prüfungen" heading under it and nothing beneath that. For them the
 * tests are not there at all, so the group reads as it does at a station that runs none.
 */
describe('QuizSidebarGroup', () => {
    function signIn(permissions: string[]) {
        sessionInfo.value = {
            permissions,
            disabledModules: [],
            member: {id: 1},
        } as unknown as SessionInfo
    }

    afterEach(() => {
        sessionInfo.value = null
    })

    it('names both halves for somebody who has both', async () => {
        signIn(['PROTOCOL_CREATE'])

        const group = await mountSuspended(QuizSidebarGroup, {props: {openGroup: null, isDesktop: true}})

        expect(group.text()).toContain('Quiz & Prüfungen')
        expect(group.text()).toContain('Prüfungen')
    })

    it('leaves the tests out for somebody who has nothing to do with them', async () => {
        signIn([])

        const group = await mountSuspended(QuizSidebarGroup, {props: {openGroup: null, isDesktop: true}})

        expect(group.text()).not.toContain('Prüfungen')
        expect(group.text()).toContain('Quiz')
    })
})
