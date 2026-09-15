/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import PatchNotesView from './PatchNotesView.vue'

const getChangelog = vi.fn()
const get = vi.fn()

vi.mock('@/api/system', () => ({
    getChangelog: (...args: unknown[]) => getChangelog(...args),
}))

vi.mock('@/api/client', () => ({
    default: {get: (...args: unknown[]) => get(...args)},
}))

/**
 * What the instance says it changed.
 *
 * <p>The entries come from the instance rather than from GitHub, which is the whole point of the
 * page: an installation with no way out can still say what it changed, and nobody has to leave an
 * address somewhere else to read it. What the page decides on its own is which version is the one
 * being run.
 */
describe('PatchNotesView', () => {
    function page() {
        return mount(PatchNotesView, {
            global: {stubs: {'router-link': {template: '<a><slot/></a>'}}},
        })
    }

    async function settled(view: ReturnType<typeof page>) {
        await Promise.resolve()
        await Promise.resolve()
        await Promise.resolve()
        await view.vm.$nextTick()
        return view
    }

    beforeEach(() => {
        vi.clearAllMocks()
        getChangelog.mockResolvedValue([
            {version: '26.17.0', body: '### Neue Funktionen\n\n- **Dateien am Termin.** Etwas Neues.'},
            {version: '26.16.0', body: '### Fehlerbehebungen\n\n- **Etwas war falsch.** Jetzt nicht mehr.'},
        ])
        get.mockResolvedValue({data: {version: '26.17.0'}})
    })

    it('reads what it shows from the instance and not from GitHub', async () => {
        const fetchSpy = vi.spyOn(globalThis, 'fetch')

        const view = await settled(page())

        expect(getChangelog).toHaveBeenCalled()
        expect(fetchSpy, 'nothing is asked of anybody else').not.toHaveBeenCalled()
        expect(view.findAll('[data-testid="changelog-version"]')).toHaveLength(2)
    })

    it('renders the entries as they were written', async () => {
        const view = await settled(page())

        expect(view.text()).toContain('Dateien am Termin')
        expect(view.html()).toContain('<li>')
    })

    /** The question behind the page is usually what changed for us, not what has been released. */
    it('marks the version this instance runs', async () => {
        const view = await settled(page())
        const versions = view.findAll('[data-testid="changelog-version"]')

        expect(versions[0]!.find('[data-testid="changelog-current"]').exists()).toBe(true)
        expect(versions[1]!.find('[data-testid="changelog-current"]').exists()).toBe(false)
    })

    /** A build off a branch says more than the numbers, and the changelog is keyed by the numbers. */
    it('finds the running version in what a branch build reports', async () => {
        get.mockResolvedValue({data: {version: '26.17.0 main-1a2b3c4 @ 2026-09-15 10:00'}})

        const view = await settled(page())

        expect(view.find('[data-testid="changelog-current"]').exists()).toBe(true)
    })

    it('says so where the changelog could not be read', async () => {
        getChangelog.mockRejectedValue(new Error('nope'))

        const view = await settled(page())

        expect(view.findAll('[data-testid="changelog-version"]')).toHaveLength(0)
        expect(view.text()).toContain('konnten nicht geladen werden')
    })

    /** An instance that cannot say which version it runs still shows what the versions brought. */
    it('shows the entries even where the version is not known', async () => {
        get.mockRejectedValue(new Error('nope'))

        const view = await settled(page())

        expect(view.findAll('[data-testid="changelog-version"]')).toHaveLength(2)
        expect(view.find('[data-testid="changelog-current"]').exists()).toBe(false)
    })
})
