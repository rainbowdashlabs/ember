/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import {reactive, ref} from 'vue'
import NavSidebar from './NavSidebar.vue'
import type {NavNode} from '@/data/sidebar/navTypes'

const route = reactive({path: '/station/news'})
const push = vi.fn(async (to: string) => {
    route.path = to
})
const collapsed = ref(false)
const tree = ref(false)

vi.mock('@/composables/useSidebarMode', () => ({
    useSidebarMode: () => ({tree}),
}))

vi.mock('vue-router', () => ({
    useRoute: () => route,
    useRouter: () => ({push}),
}))

vi.mock('@/composables/useSidebarCollapse', () => ({
    useSidebarCollapse: () => ({collapsed}),
}))

vi.mock('@/composables/useBreakpoint', () => ({
    useBreakpoint: () => ({isDesktop: ref(true), isMobile: ref(false)}),
}))

vi.mock('@/composables/useSidebarSubgroups', () => ({
    useSidebarSubgroups: () => ({chosen: ref({}), restore: vi.fn(), setOpen: vi.fn()}),
}))

function page(key: string, to: string, label = key): NavNode {
    return {kind: 'page', key, label, icon: 'circle', to, badge: 0, dimmed: false, paletteOnly: false}
}

const NODES: NavNode[] = [
    page('news', '/station/news', 'Neuigkeiten'),
    {
        kind: 'group', key: 'members', label: 'Mitglieder', icon: 'users', to: '/station/members/list',
        prefix: ['/station/members', '/station/members/list', '/station/members/create'], badge: 0, inline: false, holds: [],
        children: [page('members-list', '/station/members/list', 'Mitgliederliste'), page('members-create', '/station/members/create', 'Anlegen')],
    },
    {
        kind: 'group', key: 'events', label: 'Termine', icon: 'calendar', to: '/station/events/upcoming',
        prefix: ['/station/events/upcoming'], badge: 0, inline: false, holds: [],
        children: [page('events-upcoming', '/station/events/upcoming', 'Kommende Termine'), page('events-manage', '/station/events', 'Verwalten')],
    },
]

const RouterLink = {
    template: '<slot v-if="custom" :href="to" :navigate="navigate"/><a v-else :href="to" @click="$emit(\'click\')"><slot/></a>',
    props: {to: String, custom: Boolean},
    emits: ['click'],
    methods: {
        navigate(this: { to: string }, event: MouseEvent) {
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return
            event.preventDefault()
            return push(this.to)
        },
    },
}

function mountSidebar() {
    return mount(NavSidebar, {props: {nodes: NODES}, attachTo: document.body, global: {stubs: {'router-link': RouterLink, RouterLink, transition: true}}})
}

/**
 * The sidebar shows one section at a time: opening one replaces the list, back returns to it, and arriving
 * on a page shows the section that page is in.
 */
describe('NavSidebar', () => {
    beforeEach(() => {
        route.path = '/station/news'
        collapsed.value = false
        tree.value = false
        push.mockClear()
    })

    afterEach(() => {
        document.body.innerHTML = ''
    })

    it('lists pages as links and sections as rows that open', () => {
        const sidebar = mountSidebar()

        expect(sidebar.find('a[href="/station/news"]').exists()).toBe(true)
        expect(sidebar.find('[data-section="members"]').exists()).toBe(true)
        expect(sidebar.text()).not.toContain('Mitgliederliste')
    })

    it('opens a section in place of the list and opens its first page', async () => {
        const sidebar = mountSidebar()

        await sidebar.find('[data-section="members"]').trigger('click')
        await flushPromises()

        expect(push).toHaveBeenCalledWith('/station/members/list')
        expect(sidebar.text()).toContain('Mitgliederliste')
        expect(sidebar.text()).toContain('Alle Bereiche')
        expect(sidebar.find('[data-section="events"]').exists()).toBe(false)
    })

    it('links a section to its first page and leaves a modified click to the browser', async () => {
        const sidebar = mountSidebar()
        const row = sidebar.find('[data-section="members"]')

        await row.trigger('click', {ctrlKey: true})
        await flushPromises()

        expect(row.attributes('href')).toBe('/station/members/list')
        expect(push).not.toHaveBeenCalled()
        expect(sidebar.text()).not.toContain('Mitgliederliste')
    })

    it('goes back to the full list with focus on the section just left', async () => {
        const sidebar = mountSidebar()
        await sidebar.find('[data-section="members"]').trigger('click')
        await flushPromises()

        await sidebar.find('[data-back]').trigger('click')
        await flushPromises()

        expect(sidebar.find('[data-section="events"]').exists()).toBe(true)
        expect(document.activeElement?.getAttribute('data-section')).toBe('members')
    })

    it('goes back on Escape', async () => {
        const sidebar = mountSidebar()
        await sidebar.find('[data-section="members"]').trigger('click')
        await flushPromises()

        await sidebar.find('[data-back]').trigger('keydown', {key: 'Escape'})
        await flushPromises()

        expect(sidebar.find('[data-section="members"]').exists()).toBe(true)
    })

    it('shows the section of a page reached by a link', async () => {
        const sidebar = mountSidebar()

        route.path = '/station/events/upcoming'
        await flushPromises()

        expect(sidebar.text()).toContain('Kommende Termine')
        expect(sidebar.find('[data-section="members"]').exists()).toBe(false)
    })

    /**
     * While the session loads, a section can hold a single page and stand as that page under the
     * section's own key. Once the rest of it arrives the section has to open, though neither the
     * address nor the key changed.
     */
    it('opens the section of the page shown once it grows from a single page into a section', async () => {
        route.path = '/station/members/list'
        const members = NODES[1] as Extract<NavNode, {kind: 'group'}>
        const sidebar = mount(NavSidebar, {
            props: {nodes: [page('members', '/station/members/list', 'Mitglieder')]},
            attachTo: document.body,
            global: {stubs: {'router-link': RouterLink, RouterLink, transition: true}},
        })
        expect(sidebar.text()).not.toContain('Alle Bereiche')

        await sidebar.setProps({nodes: [members]})
        await flushPromises()

        expect(sidebar.text()).toContain('Alle Bereiche')
        expect(sidebar.text()).toContain('Mitgliederliste')
    })

    it('stays on the full list after going back, even when the section grows meanwhile', async () => {
        route.path = '/station/members/list'
        const members = NODES[1] as Extract<NavNode, {kind: 'group'}>
        const sidebar = mount(NavSidebar, {
            props: {nodes: [members]},
            attachTo: document.body,
            global: {stubs: {'router-link': RouterLink, RouterLink, transition: true}},
        })
        await sidebar.find('[data-back]').trigger('click')
        await flushPromises()

        await sidebar.setProps({nodes: [page('members', '/station/members/list', 'Mitglieder')]})
        await sidebar.setProps({nodes: [members]})
        await flushPromises()

        expect(sidebar.find('[data-section="members"]').exists()).toBe(true)
        expect(sidebar.text()).not.toContain('Alle Bereiche')
    })

    it('lights the section holding the page in the full list', async () => {
        route.path = '/station/members/create'
        const sidebar = mountSidebar()
        await sidebar.find('[data-back]').trigger('click')
        await flushPromises()

        expect(sidebar.find('[data-section="members"]').attributes('data-active')).toBe('true')
        expect(sidebar.find('[data-section="events"]').attributes('data-active')).toBeUndefined()
    })

    it('keeps the icon rail with its flyouts when collapsed', () => {
        collapsed.value = true
        const sidebar = mountSidebar()

        expect(sidebar.find('[data-section]').exists()).toBe(false)
        expect(sidebar.find('[data-back]').exists()).toBe(false)
        expect(sidebar.find('a[href="/station/members/list"]').exists()).toBe(true)
    })

    it('shows every section at once, the one holding the page opened in place, for an account that chose the tree', async () => {
        tree.value = true
        route.path = '/station/events/upcoming'
        const sidebar = mountSidebar()
        await flushPromises()

        expect(sidebar.find('[data-section]').exists()).toBe(false)
        expect(sidebar.find('[data-back]').exists()).toBe(false)
        expect(sidebar.text()).toContain('Mitglieder')
        expect(sidebar.text()).toContain('Termine')
        expect(sidebar.find('a[href="/station/events"]').exists()).toBe(true)
        expect(sidebar.find('a[href="/station/members/create"]').exists()).toBe(false)
    })
})
