/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, reactive, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {useDebounceFn} from '@vueuse/core'
import {adminAccounts, clusters, stations} from '@/api'
import type {AccountFilterParams} from '@/api/adminAccounts'
import type {AccountOverview} from '@/api/generated/schema'
import type {DataTableState} from '@/composables/useDataTable'
import {describeFailure, type Failure} from '@/util/failure'
import {accountParams, accountTableState, viewOfState, type AccountTableView} from './accountQuery'
import type {AccountPlaces} from './accountColumns'

/** How many accounts one page of the list holds. */
export const ACCOUNT_PAGE_SIZE = 25

const SEARCH_DELAY_MS = 300

function searchOf(query: Readonly<Record<string, unknown>>): string {
    return typeof query.q === 'string' ? query.q : ''
}

function sameParams(a: AccountFilterParams, b: AccountFilterParams): boolean {
    return JSON.stringify(a) === JSON.stringify(b)
}

/**
 * The instance's account list, a page at a time: the search, the filters, the order and the paging
 * are the server's, so the list stays the same size however many people the instance holds and a
 * filter speaks for every account rather than the page on screen.
 *
 * <p>The table's sort and filters live in {@link state}, which the address fills on the way in and
 * again whenever it changes from outside, so a link such as `/admin/accounts?confirmed=false` opens
 * the list with that filter set. Once the screen hands {@link follow} the table's view, every change
 * of a filter or the order asks the server again from the first page and writes the address anew.
 *
 * <p>Only the answer to the latest request is shown: one that arrives after a newer one was sent is
 * dropped, so a quick change of filters never ends on the rows of the filter before.
 */
/**
 * One spelling of an address's query, so the address this list wrote itself is recognised when the
 * router hands it back, whatever order its parameters come in.
 */
function addressKey(address: Readonly<Record<string, unknown>>): string {
    return JSON.stringify(Object.keys(address).sort().map(key => [key, address[key]]))
}

export function useAccountList() {
    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const state = reactive(accountTableState(route.query)) as DataTableState
    const query = ref(searchOf(route.query))
    const filters = ref<AccountFilterParams>({})
    const page = ref(0)
    const total = ref(0)
    const accounts = ref<AccountOverview[]>([])
    const places = ref<AccountPlaces>({stations: [], associations: []})
    const loading = ref(false)
    const failure = ref<Failure | null>(null)
    let latestRequest = 0
    let lastWritten = ''
    const ownPath = route.path

    const pages = computed(() => Math.max(1, Math.ceil(total.value / ACCOUNT_PAGE_SIZE)))

    async function load() {
        const request = ++latestRequest
        loading.value = true
        failure.value = null
        try {
            const answer = await adminAccounts.listAccounts({
                q: query.value.trim(), page: page.value, size: ACCOUNT_PAGE_SIZE, filters: filters.value,
            })
            if (request !== latestRequest) return
            accounts.value = answer.accounts
            total.value = answer.total
        } catch (e) {
            if (request === latestRequest) failure.value = describeFailure(e, t)
        } finally {
            if (request === latestRequest) loading.value = false
        }
    }

    function writeAddress() {
        const q = query.value.trim()
        const address = q ? {...filters.value, q} : {...filters.value}
        lastWritten = addressKey(address)
        void router.replace({query: address})
    }

    const searchLater = useDebounceFn(() => {
        page.value = 0
        writeAddress()
        return load()
    }, SEARCH_DELAY_MS)

    function goTo(target: number) {
        page.value = Math.min(Math.max(target, 0), pages.value - 1)
        return load()
    }

    /** The columns the address filters on, which have to show for their filters to stand. */
    function filteredColumns(): string[] {
        return [...viewOfState(state).filters.keys()]
    }

    /**
     * Takes over an address that changed from outside, such as the sidebar leading back to the
     * unfiltered list. An address this list wrote itself changes nothing.
     */
    function readAddress(address: Readonly<Record<string, unknown>>, show: (columns: string[]) => void) {
        const next = accountTableState(address)
        const nextSearch = searchOf(address)
        if (sameParams(accountParams(viewOfState(next)), filters.value) && nextSearch === query.value.trim()) return
        Object.assign(state, next)
        query.value = nextSearch
        show(filteredColumns())
        filters.value = accountParams(viewOfState(next))
        page.value = 0
        void load()
    }

    /**
     * Asks the server by the table's sort and filters from now on: the first answer reads what the
     * address set, and each change after it starts again at the first page.
     *
     * @param view what the table shows of its sort and filters
     * @param show makes the given columns show, so a filter the address sets is not dropped for a
     *             column the reader once hid
     */
    function follow(view: () => AccountTableView, show: (columns: string[]) => void) {
        show(filteredColumns())
        filters.value = accountParams(view())
        watch(() => accountParams(view()), next => {
            if (sameParams(next, filters.value)) return
            filters.value = next
            page.value = 0
            writeAddress()
            void load()
        })
        watch(() => route.query, address => {
            if (route.path !== ownPath || addressKey(address) === lastWritten) return
            readAddress(address, show)
        })
    }

    /** The stations and associations the filters offer, by uid and name. Without them, they offer none. */
    async function loadPlaces() {
        const [stationList, associationList] = await Promise.all([
            stations.listStations().catch(() => []),
            clusters.listAll().catch(() => []),
        ])
        places.value = {
            stations: stationList.map(station => ({value: station.id, label: station.name}))
                .toSorted((a, b) => a.label.localeCompare(b.label)),
            associations: associationList.map(association => ({value: association.uid, label: association.name}))
                .toSorted((a, b) => a.label.localeCompare(b.label)),
        }
    }

    return {
        state, query, page, pages, total, accounts, places, loading, failure,
        load, loadPlaces, follow, search: searchLater, goTo,
    }
}
