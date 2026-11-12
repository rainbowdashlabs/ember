/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AccountFilterParams} from '@/api/adminAccounts'
import {AccountPasswordState, AccountSort, InstanceUserType} from '@/api/generated/schema'
import {joinNumberTokens, splitNumberTokens} from '@/components/table/columnFilter'
import {emptyTableState, type DataTableState, type StandingFilter} from '@/composables/useDataTable'
import type {SortDirection} from '@/composables/useSortable'
import {splitDateTokens} from '@/util/dateFilter'
import {toIsoDate} from '@/util/format'

/** How the account table is being looked at, as far as the server is asked about it. */
export interface AccountTableView {
    sortKey: string | null
    sortDirection: SortDirection
    filters: ReadonlyMap<string, StandingFilter>
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const DAY_PATTERN = /^\d{4}-\d{2}-\d{2}$/

/**
 * A list filter: the column it stands on, the parameter its values go to, the one its empties go to,
 * and which values an address may set for it.
 */
interface ListFilter {
    column: string
    param: string
    empty?: string
    accepts: (value: string) => boolean
}

const ROLES: ReadonlySet<string> = new Set(Object.values(InstanceUserType))
const PASSWORD_STATES: ReadonlySet<string> = new Set(Object.values(AccountPasswordState))

const LIST_FILTERS: readonly ListFilter[] = [
    {column: 'role', param: 'role', accepts: value => ROLES.has(value)},
    {column: 'password', param: 'password', accepts: value => PASSWORD_STATES.has(value)},
    {column: 'stations', param: 'station', empty: 'withoutStation', accepts: value => UUID_PATTERN.test(value)},
    {
        column: 'associations', param: 'association', empty: 'withoutAssociation',
        accepts: value => UUID_PATTERN.test(value),
    },
]

/** A yes or no filter: the column, which is also its parameter, and the parameter its empties go to. */
const YES_NO_FILTERS: readonly {column: string, empty?: string}[] = [
    {column: 'twoFactor'},
    {column: 'confirmed', empty: 'withoutAddress'},
]

const SORT_PARAMS: Readonly<Record<string, AccountSort>> = {
    name: AccountSort.NAME, lastSignIn: AccountSort.LAST_SIGN_IN, passkeys: AccountSort.PASSKEYS,
}

const SIGN_IN_COLUMN = 'lastSignIn'
const PASSKEY_COLUMN = 'passkeys'
const DEFAULT_SORT_COLUMN = 'name'

/** The day before or after an ISO day, by the reader's calendar. */
function shiftDay(day: string, days: number): string {
    const date = new Date(`${day}T12:00:00`)
    date.setDate(date.getDate() + days)
    return toIsoDate(date)
}

/** A yes or no filter as one answer, or nothing where both or neither are ticked. */
function yesOrNo(filter: StandingFilter): string | null {
    const answers = [...filter.selected].filter(value => value === 'true' || value === 'false')
    return answers.length === 1 ? answers[0] ?? null : null
}

function addSignIn(params: AccountFilterParams, filter: StandingFilter) {
    const tokens = splitDateTokens(filter.selected)
    if (tokens.from) params.signedInFrom = tokens.from
    if (tokens.before) params.signedInTo = shiftDay(tokens.before, -1)
    if (filter.includeEmpty) params.neverSignedIn = 'true'
}

function addPasskeys(params: AccountFilterParams, filter: StandingFilter) {
    const tokens = splitNumberTokens(filter.selected)
    if (tokens.min !== null) params.passkeysMin = String(tokens.min)
    if (tokens.max !== null) params.passkeysMax = String(tokens.max)
}

/**
 * The query parameters the account list asks the server for, from the table's sort and the filters
 * standing on its shown columns: the options of a list to their parameter, a yes or no to one
 * answer, the last sign-in's range to its first and last day, the passkeys' range to its bounds, and
 * each empty choice to the parameter that names it ("never signed in", "in no station", "no address
 * of its own"). The order is left out while it is the default.
 */
export function accountParams(view: AccountTableView): AccountFilterParams {
    const params: AccountFilterParams = {}
    for (const list of LIST_FILTERS) {
        const filter = view.filters.get(list.column)
        if (!filter) continue
        if (filter.selected.size > 0) params[list.param] = [...filter.selected]
        if (list.empty && filter.includeEmpty) params[list.empty] = 'true'
    }
    for (const yesNo of YES_NO_FILTERS) {
        const filter = view.filters.get(yesNo.column)
        if (!filter) continue
        const answer = yesOrNo(filter)
        if (answer) params[yesNo.column] = answer
        if (yesNo.empty && filter.includeEmpty) params[yesNo.empty] = 'true'
    }
    const signIn = view.filters.get(SIGN_IN_COLUMN)
    if (signIn) addSignIn(params, signIn)
    const passkeys = view.filters.get(PASSKEY_COLUMN)
    if (passkeys) addPasskeys(params, passkeys)
    const sort = view.sortKey ? SORT_PARAMS[view.sortKey] : undefined
    if (sort && (sort !== AccountSort.NAME || view.sortDirection === 'desc')) {
        params.sort = sort
        params.direction = view.sortDirection
    }
    return params
}

/**
 * The view a table state stands for with every filter it holds, whether or not its column shows:
 * what an address set, before the table has had a say.
 */
export function viewOfState(state: DataTableState): AccountTableView {
    const keys = new Set([...state.filters.keys(), ...state.empties])
    const filters = new Map([...keys].map(key => [key, {
        selected: state.filters.get(key) ?? new Set<string>(),
        includeEmpty: state.empties.has(key),
    }]))
    return {sortKey: state.sortKey, sortDirection: state.sortDirection, filters}
}

/** Every value an address names for one parameter, whether it names it once or several times. */
function valuesOf(query: Readonly<Record<string, unknown>>, name: string): string[] {
    const raw = query[name]
    const all = Array.isArray(raw) ? raw : [raw]
    return all.filter((value): value is string => typeof value === 'string' && value !== '')
}

function firstOf(query: Readonly<Record<string, unknown>>, name: string): string | null {
    return valuesOf(query, name)[0] ?? null
}

function isTrue(query: Readonly<Record<string, unknown>>, name: string): boolean {
    return firstOf(query, name) === 'true'
}

function dayOf(query: Readonly<Record<string, unknown>>, name: string): string | null {
    const day = firstOf(query, name)
    return day && DAY_PATTERN.test(day) ? day : null
}

function boundOf(query: Readonly<Record<string, unknown>>, name: string): number | null {
    const raw = firstOf(query, name)
    if (raw === null || !/^\d+$/.test(raw)) return null
    return Number(raw)
}

function readSignIn(state: DataTableState, query: Readonly<Record<string, unknown>>) {
    const from = dayOf(query, 'signedInFrom')
    const to = dayOf(query, 'signedInTo')
    const signIn = new Set<string>()
    if (from) signIn.add('from:' + from)
    if (to) signIn.add('before:' + shiftDay(to, 1))
    if (signIn.size > 0) state.filters.set(SIGN_IN_COLUMN, signIn)
    if (isTrue(query, 'neverSignedIn')) state.empties.add(SIGN_IN_COLUMN)
}

/**
 * The table state an address describes, the way back from {@link accountParams}: what an overview
 * card links to (`/admin/accounts?confirmed=false`) opens with that filter set. Anything the address
 * does not name, and anything it names that is no valid value, stays open.
 */
export function accountTableState(query: Readonly<Record<string, unknown>>): DataTableState {
    const state = emptyTableState(DEFAULT_SORT_COLUMN)
    for (const list of LIST_FILTERS) {
        const values = valuesOf(query, list.param).filter(list.accepts)
        if (values.length > 0) state.filters.set(list.column, new Set(values))
        if (list.empty && isTrue(query, list.empty)) state.empties.add(list.column)
    }
    for (const yesNo of YES_NO_FILTERS) {
        const answer = firstOf(query, yesNo.column)
        if (answer === 'true' || answer === 'false') state.filters.set(yesNo.column, new Set([answer]))
        if (yesNo.empty && isTrue(query, yesNo.empty)) state.empties.add(yesNo.column)
    }
    readSignIn(state, query)
    const passkeys = joinNumberTokens(boundOf(query, 'passkeysMin'), boundOf(query, 'passkeysMax'))
    if (passkeys.size > 0) state.filters.set(PASSKEY_COLUMN, passkeys)
    const sort = firstOf(query, 'sort')
    const column = Object.keys(SORT_PARAMS).find(key => SORT_PARAMS[key] === sort)
    if (column) state.sortKey = column
    if (firstOf(query, 'direction') === 'desc') state.sortDirection = 'desc'
    return state
}
