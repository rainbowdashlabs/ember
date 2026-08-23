/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref } from 'vue'
import { demo } from '@/api'
import type { DemoAccount, DemoAccountsPayload, DemoStationGroup } from '@/api/demo'
import { StationUserType, StationUserTypeLabels } from '@/api/types'

export type { DemoAccount }

/** The band a person with no station of their own is offered under. */
const NO_STATION = 'Ohne Wache'

/**
 * A named group of demo accounts shown together on the demo login UI (e.g. "Admin", "Team").
 */
export interface RoleGroup {
  label: string
  accounts: DemoAccount[]
}

/**
 * A station to pick on the demo login, with how many people it has to offer.
 *
 * The count is what tells the demo's two full stations apart from the handful of spares beside them,
 * which a row of equal-looking names never did.
 */
export interface StationChoice {
  key: string
  label: string
  memberCount: number
}

/**
 * Everything the account chooser draws, as one value.
 *
 * One prop rather than eight, because the demo login page and the dev footer both show the whole of
 * it and both hand it straight on to the same chooser underneath them.
 */
export interface DemoAccountsView {
  noStationRoleGroups: RoleGroup[]
  clusterRoleGroups: RoleGroup[]
  roleGroups: RoleGroup[]
  stationChoices: StationChoice[]
  showStationPicker: boolean
  searching: boolean
  searchGroups: RoleGroup[]
}

/** What somebody is at their station, in the words the account cards use. */
export function roleLabel(account: DemoAccount): string {
  return StationUserTypeLabels[account.userType as keyof typeof StationUserTypeLabels] ?? account.userType ?? 'Login'
}

/**
 * The one-click demo logins offered on demo and dev instances, grouped by station and then by
 * role so the list is navigable.
 *
 * An instance that is neither demo nor dev exposes nothing here, and a failure to reach the
 * status endpoint is treated the same way - the login page must still render for a normal
 * instance where these endpoints do not exist.
 */
export function useDemoAccounts() {
  const isDemo = ref(false)
  const isDev = ref(false)
  const loading = ref(true)

  const stationGroups = ref<DemoStationGroup[]>([])
  const noStationAccounts = ref<DemoAccount[]>([])
  const activeStation = ref('')
  const search = ref('')

  const hasDemoAccounts = computed(() =>
    noStationAccounts.value.length > 0 || stationGroups.value.some(g => g.accounts.length > 0),
  )

  const stationChoices = computed<StationChoice[]>(() =>
    stationGroups.value.map(g => ({key: g.stationId, label: g.stationName, memberCount: g.accounts.length})),
  )
  const showStationPicker = computed(() => stationGroups.value.length > 1)

  const activeAccounts = computed(() =>
    stationGroups.value.find(g => g.stationId === activeStation.value)?.accounts ?? [],
  )

  /**
   * Splits accounts into role bands in a fixed order, each account landing in the first band it
   * matches so no account is offered twice.
   */
  function buildRoleGroups(source: DemoAccount[]): RoleGroup[] {
    const groups: RoleGroup[] = []
    const seen = new Set<string>()

    function addGroup(label: string, filter: (a: DemoAccount) => boolean) {
      const matching = source.filter(a => !seen.has(a.email) && filter(a))
      if (!matching.length) return
      groups.push({label, accounts: matching})
      matching.forEach(a => seen.add(a.email))
    }

    addGroup('Admin', a => a.userType === StationUserType.MANAGER)
    addGroup('Team', a => a.userType === StationUserType.TEAM)
    addGroup('Erziehungsberechtigter', a => a.userType === StationUserType.GUARDIAN)
    addGroup('Mitglieder', a => a.userType === StationUserType.MEMBER || a.userType === StationUserType.TRIAL)
    return groups
  }

  const roleGroups = computed(() => buildRoleGroups(activeAccounts.value))
  const noStationRoleGroups = computed(() => buildRoleGroups(noStationAccounts.value))

  /**
   * The accounts that act for an association, gathered by the job they do there.
   *
   * Across every station rather than under one, because acting for an association has nothing to do with
   * which station somebody is at, and one of these people is at no station at all. Without this the only
   * way to sign in as the association's gear manager is to know which station they happen to belong to.
   */
  const clusterRoleGroups = computed<RoleGroup[]>(() => {
    const everybody = [...noStationAccounts.value, ...stationGroups.value.flatMap(g => g.accounts)]
    const seen = new Set<string>()
    const acting = everybody.filter(a => {
      if (!a.email || seen.has(a.email) || !(a.clusterPermissions ?? []).length) return false
      seen.add(a.email)
      return true
    })

    const groups: RoleGroup[] = []
    const taken = new Set<string>()

    function addGroup(label: string, holds: string) {
      const matching = acting.filter(a => !taken.has(a.email) && (a.clusterPermissions ?? []).includes(holds))
      if (!matching.length) return
      groups.push({label, accounts: matching})
      matching.forEach(a => taken.add(a.email))
    }

    addGroup('Verbandsleitung', 'CLUSTER_ADMINISTRATOR')
    addGroup('Mitgliederverwaltung', 'CLUSTER_MEMBER_MANAGER')
    addGroup('Materialverwaltung', 'CLUSTER_INVENTORY_MANAGER')
    const rest = acting.filter(a => !taken.has(a.email))
    if (rest.length) groups.push({label: 'Verband', accounts: rest})
    return groups
  })

  /** Everything about somebody the search reads: who they are, how they sign in, and what they are. */
  function haystack(account: DemoAccount): string[] {
    return [
      `${account.firstName} ${account.lastName}`,
      account.email,
      roleLabel(account),
      ...account.groups,
      ...account.tags,
    ]
  }

  const searching = computed(() => search.value.trim().length > 0)

  /**
   * Everybody the search turns up, in bands named after the station they are at.
   *
   * Across every station at once rather than inside the one that happens to be picked: what is being
   * asked is "somebody who is a guardian", and which station they are at is the answer to that rather
   * than something to know beforehand.
   */
  const searchGroups = computed<RoleGroup[]>(() => {
    const needle = search.value.trim().toLowerCase()
    if (!needle) return []

    const matching = (accounts: DemoAccount[]) => accounts.filter(account =>
      haystack(account).some(value => value?.toLowerCase().includes(needle)))

    const bands: RoleGroup[] = []
    const withoutStation = matching(noStationAccounts.value)
    if (withoutStation.length) bands.push({label: NO_STATION, accounts: withoutStation})
    for (const group of stationGroups.value) {
      const found = matching(group.accounts)
      if (found.length) bands.push({label: group.stationName, accounts: found})
    }
    return bands
  })

  /**
   * Accepts both the grouped payload and the two flat shapes older instances return, so a demo
   * instance one version behind still offers its accounts.
   */
  function applyPayload(payload: DemoAccountsPayload) {
    if (!Array.isArray(payload)) {
      stationGroups.value = payload.stationGroups ?? []
      noStationAccounts.value = payload.noStationAccounts ?? []
      return
    }
    const [firstEntry] = payload
    stationGroups.value = firstEntry && 'accounts' in firstEntry
      ? (payload as DemoStationGroup[])
      : [{stationId: 'default', stationName: 'Station', accounts: payload as DemoAccount[]}]
    noStationAccounts.value = []
  }

  async function load() {
    try {
      const status = await demo.getDemoStatus()
      isDemo.value = status.demo
      isDev.value = status.dev
      if (isDemo.value || isDev.value) {
        applyPayload(await demo.getDemoAccounts())
        activeStation.value = stationGroups.value[0]?.stationId ?? ''
      }
    } catch {
      isDemo.value = false
      isDev.value = false
    }
    loading.value = false
  }

  const view = computed<DemoAccountsView>(() => ({
    noStationRoleGroups: noStationRoleGroups.value,
    clusterRoleGroups: clusterRoleGroups.value,
    roleGroups: roleGroups.value,
    stationChoices: stationChoices.value,
    showStationPicker: showStationPicker.value,
    searching: searching.value,
    searchGroups: searchGroups.value,
  }))

  return {
    isDemo,
    isDev,
    loading,
    activeStation,
    search,
    hasDemoAccounts,
    view,
    load,
  }
}
