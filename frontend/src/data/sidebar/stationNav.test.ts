/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {describe, expect, it} from 'vitest'
import {StationModule, StationPermission, type SidebarCounts} from '@/api/generated/schema'
import {STATION_NAV, type StationNavContext} from '@/data/sidebar/stationNav'
import type {NavNode} from '@/data/sidebar/navTypes'
import {navPages, resolveNav} from '@/util/navTree'

/**
 * The station sidebar for one reader: what they may reach is there, and nothing else.
 */
describe('STATION_NAV', () => {
    function readerWith(permissions: string[], modules: string[] = Object.values(StationModule)): StationNavContext {
        const session = {
            hasPermission: (permission: string) => permissions.includes(permission),
            isModuleEnabled: (module: string) => modules.includes(module),
            canTestProtocol: () => permissions.includes(StationPermission.PROTOCOL_TESTER),
            isManager: () => permissions.includes(StationPermission.STATION_ADMINISTRATOR),
            hasAnyMemberPermission: () => permissions.some(permission => permission.startsWith('MEMBER_')),
            sessionInfo: {value: {setupCompletedAt: '2026-01-01T00:00:00Z'}},
        }
        const everythingElse = new Proxy(session, {get: (target, name) => name in target ? target[name as keyof typeof target] : () => false})
        return {
            session: everythingElse as unknown as StationNavContext['session'],
            counts: new Proxy({}, {get: () => 0}) as SidebarCounts,
            boards: [],
            boardBookmarks: [],
            setupSteps: [],
        }
    }

    function section(nodes: NavNode[], key: string) {
        return nodes.find(node => node.key === key)
    }

    function nav(permissions: string[], modules?: string[]) {
        return resolveNav(STATION_NAV, readerWith(permissions, modules), key => key)
    }

    describe('documents', () => {
        function documentPages(permissions: string[]) {
            return navPages([section(nav(permissions), 'documents')!]).map(page => page.to)
        }

        it('leads a member without rights to their own documents and to creating documents for themselves', () => {
            expect(documentPages([])).toEqual(['/station/documents', '/station/documents/self-service'])
        })

        it('shows the store and the generated documents to whoever reads them', () => {
            expect(documentPages([StationPermission.DOCUMENT_READ, StationPermission.DOCUMENT_READ_MEMBER])).toEqual([
                '/station/documents', '/station/documents/self-service', '/station/documents/store', '/station/documents/generated',
            ])
        })

        it('shows templates and fonts to whoever writes templates', () => {
            expect(documentPages([StationPermission.DOCUMENT_TEMPLATE_EDIT])).toEqual([
                '/station/documents', '/station/documents/self-service', '/station/documents/templates', '/station/documents/fonts',
            ])
        })
    })

    /**
     * A member who may neither write a test sheet nor sit one was once shown a section called
     * "Quiz & Prüfungen" with an empty "Prüfungen" heading. For them the tests are not there at all.
     */
    describe('quiz and tests', () => {
        it('names both halves and splits them for somebody who has both', () => {
            const quiz = section(nav([StationPermission.PROTOCOL_CREATE]), 'quiz-protocols')

            expect(quiz?.label).toBe('sidebar.quizAndProtocols')
            expect(quiz?.kind === 'group' && quiz.children.map(child => child.kind === 'group' && child.inline)).toEqual([false, false])
        })

        it('leaves the tests out for somebody who has nothing to do with them', () => {
            const quiz = section(nav([]), 'quiz-protocols')

            expect(quiz?.label).toBe('sidebar.quiz')
            expect(navPages([quiz!]).some(page => page.to.startsWith('/station/protocols'))).toBe(false)
        })

        it('is not there at a station that runs neither', () => {
            expect(section(nav([StationPermission.PROTOCOL_CREATE], []), 'quiz-protocols')).toBeUndefined()
        })
    })

    it('keeps pages reached from inside others out of the listing but in the palette', () => {
        const manage = section(nav([StationPermission.STATION_ADMINISTRATOR]), 'manage')

        expect(navPages([manage!]).find(page => page.to === '/station/manage/storage/backend')?.paletteOnly).toBe(true)
    })

    describe('one kind per entry', () => {
        it('opens a section on its first page, which is the page the section is about', () => {
            const members = section(nav([StationPermission.MEMBER_READ, StationPermission.MEMBER_EDIT]), 'members')

            expect(members?.kind).toBe('group')
            expect(members?.to).toBe('/station/members/list')
            expect(members?.kind === 'group' && members.children[0]?.label).toBe('sidebar.memberList')
        })

        it('opens the first page the reader may see when the landing page is not theirs', () => {
            const members = section(nav([StationPermission.MEMBER_EDIT]), 'members')

            expect(members?.to).toBe('/station/members/create')
        })

        it('turns a section left with only its landing page into a page under the section name', () => {
            const forms = section(nav([]), 'forms')

            expect(forms).toMatchObject({kind: 'page', label: 'sidebar.forms', to: '/station/forms'})
        })

        it('keeps a section that has more than its landing page', () => {
            expect(section(nav([StationPermission.POLL_CREATE]), 'forms')?.kind).toBe('group')
        })
    })
})
