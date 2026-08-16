/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref } from 'vue'
import { demo } from '@/api'
import type { DemoAccount, DemoAccountsPayload, DemoStationGroup } from '@/api/demo'
import { StationUserType } from '@/api/types'

export type { DemoAccount }

/**
 * A named group of demo accounts shown together on the demo login UI (e.g. "Admin", "Team").
 */
export interface RoleGroup {
  label: string
  accounts: DemoAccount[]
}

/**
 * A station tab in the demo login UI, used to switch between sets of demo accounts that belong
 * to different stations.
 */
export interface StationTab {
  key: string
  label: string
}

/**
 * The one-click demo logins offered on demo and dev instances, grouped by station and then by
 * role so the list is navigable.
 *
 * An instance that is neither demo nor dev exposes nothing here, and a failure to reach the
 * status endpoint is treated the same way — the login page must still render for a normal
 * instance where these endpoints do not exist.
 */
export function useDemoAccounts() {
  const isDemo = ref(false)
  const isDev = ref(false)
  const loading = ref(true)

  const stationGroups = ref<DemoStationGroup[]>([])
  const noStationAccounts = ref<DemoAccount[]>([])
  const activeStationTab = ref('')

  const hasDemoAccounts = computed(() =>
    noStationAccounts.value.length > 0 || stationGroups.value.some(g => g.accounts.length > 0),
  )

  const stationTabs = computed<StationTab[]>(() =>
    stationGroups.value.map(g => ({key: g.stationId, label: g.stationName})),
  )
  const showStationTabs = computed(() => stationGroups.value.length > 1)

  const activeAccounts = computed(() =>
    stationGroups.value.find(g => g.stationId === activeStationTab.value)?.accounts ?? [],
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
        activeStationTab.value = stationGroups.value[0]?.stationId ?? ''
      }
    } catch {
      isDemo.value = false
      isDev.value = false
    }
    loading.value = false
  }

  return {
    isDemo,
    isDev,
    loading,
    activeStationTab,
    hasDemoAccounts,
    stationTabs,
    showStationTabs,
    roleGroups,
    noStationAccounts,
    noStationRoleGroups,
    load,
  }
}
