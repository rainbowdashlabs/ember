/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {nextTick, reactive} from 'vue'
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import type {AccountOverview, AccountOverviewPage} from '@/api/generated/schema'
import {loadColumnChoices, saveColumnChoices} from '@/util/columnChoices'
import AdminAccountsView from './AdminAccountsView.vue'

const listAccounts = vi.fn()
const replace = vi.fn()
const route = reactive<{query: Record<string, string | string[]>}>({query: {}})

vi.mock('@/api', () => ({
    adminAccounts: {listAccounts: (...args: unknown[]) => listAccounts(...args)},
    stations: {listStations: () => Promise.resolve([{id: 'station-north', name: 'Feuerwache Nord', manager: null}])},
    clusters: {listAll: () => Promise.resolve([])},
    adminSettings: {},
    twoFactorAdmin: {},
}))

vi.mock('vue-router', async (original) => ({
    ...await original<typeof import('vue-router')>(),
    useRoute: () => route,
    useRouter: () => ({replace}),
}))

const COLUMN_MEMORY = 'all:admin-accounts'

function account(id: number, name: string): AccountOverview {
    return {
        id, uid: `00000000-0000-0000-0000-00000000000${id}`, name, email: null, loginName: name,
        instanceUserType: 'USER', lastSignInAt: null, stations: [], associations: [],
        hasPassword: true, passwordSignIn: true, oneTimePasswordExpiresAt: null, passkeys: 0, twoFactor: false,
        confirmed: null, setupMail: null,
    }
}

function pageOf(...accounts: AccountOverview[]): AccountOverviewPage {
    return {accounts, total: accounts.length, page: 0, size: 25}
}

/**
 * The instance's account list over its server-side filters: what the address sets is the first
 * thing the server is asked, a filter ticked in the table's own dialog reaches the next request and
 * the address, and only the answer to the latest request is shown. It runs in the Nuxt environment,
 * because the table keeps its column choices in request state.
 */
describe('AdminAccountsView', () => {
    enableAutoUnmount(afterEach)

    function page() {
        return mount(AdminAccountsView, {
            attachTo: document.body,
            global: {stubs: {ViewContent: {template: '<div><slot/></div>'}}},
        })
    }

    function lastFilters(): unknown {
        return listAccounts.mock.calls.at(-1)?.[0]?.filters
    }

    beforeEach(() => {
        document.body.innerHTML = ''
        localStorage.clear()
        localStorage.setItem('storage_consent', 'accepted')
        listAccounts.mockReset()
        replace.mockReset()
        listAccounts.mockResolvedValue(pageOf())
        route.query = {}
    })

    it('asks the server for the filter the address names', async () => {
        route.query = {confirmed: 'false'}

        page()
        await flushPromises()

        expect(listAccounts).toHaveBeenCalledTimes(1)
        expect(lastFilters()).toEqual({confirmed: 'false'})
    })

    it('shows a column the address filters on even where the reader hid it', async () => {
        saveColumnChoices(COLUMN_MEMORY, {confirmed: false})
        route.query = {confirmed: 'false'}

        page()
        await flushPromises()

        expect(lastFilters()).toEqual({confirmed: 'false'})
        expect(loadColumnChoices(COLUMN_MEMORY).confirmed).toBe(true)
    })

    it('sends a filter ticked in the column dialog to the server and into the address', async () => {
        const view = page()
        await flushPromises()

        await view.find('[aria-label="Filtern: Rolle auf der Instanz"]').trigger('click')
        await flushPromises()
        const dialog = document.body.querySelector('[data-testid="column-filter"]')
        const administration = [...dialog?.querySelectorAll('label') ?? []]
            .find(label => label.textContent?.includes('Administration'))
        const box = administration?.querySelector<HTMLInputElement>('input')
        if (!box) throw new Error('the role filter offers no box for the administration')
        expect(dialog?.textContent).not.toContain('Kein Wert (leer)')
        box.checked = true
        box.dispatchEvent(new Event('change'))
        document.body.querySelector<HTMLElement>('[data-testid="column-filter-apply"]')?.click()
        await flushPromises()

        expect(lastFilters()).toEqual({role: ['ADMINISTRATOR']})
        expect(listAccounts.mock.calls.at(-1)?.[0]?.page).toBe(0)
        expect(replace).toHaveBeenCalledWith({query: {role: ['ADMINISTRATOR']}})
    })

    it('shows only the answer to the latest request when two cross', async () => {
        let answerFirst: (page: AccountOverviewPage) => void = () => undefined
        let answerSecond: (page: AccountOverviewPage) => void = () => undefined
        listAccounts
            .mockReturnValueOnce(new Promise(resolve => { answerFirst = resolve }))
            .mockReturnValueOnce(new Promise(resolve => { answerSecond = resolve }))
        const view = page()
        await flushPromises()

        route.query = {confirmed: 'true'}
        await nextTick()
        await flushPromises()
        answerSecond(pageOf(account(2, 'Neue Antwort')))
        await flushPromises()
        answerFirst(pageOf(account(1, 'Alte Antwort')))
        await flushPromises()

        expect(listAccounts).toHaveBeenCalledTimes(2)
        expect(view.text()).toContain('Neue Antwort')
        expect(view.text()).not.toContain('Alte Antwort')
    })

    it('follows the address when it changes from outside', async () => {
        route.query = {confirmed: 'false'}
        page()
        await flushPromises()

        route.query = {}
        await nextTick()
        await flushPromises()

        expect(listAccounts).toHaveBeenCalledTimes(2)
        expect(lastFilters()).toEqual({})
    })
})
