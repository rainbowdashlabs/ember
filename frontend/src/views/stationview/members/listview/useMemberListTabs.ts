/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { StationUserType } from '@/api/types'
import type { ProfileField } from '@/api/profileFields'
import { fieldAudiences } from '@/composables/useFieldAudiences'
import { emptyTableState, type DataTableState } from '@/composables/useDataTable'
import type { ProfileFieldAssignment } from '@/util/profileFields'

const TAB_KEYS = ['ALL', StationUserType.TRIAL, StationUserType.MEMBER, StationUserType.GUARDIAN,
  StationUserType.TEAM, StationUserType.MANAGER] as const

const EVERY_KIND: string[] = [StationUserType.TRIAL, StationUserType.MEMBER, StationUserType.GUARDIAN,
  StationUserType.TEAM, StationUserType.MANAGER]

/**
 * The member list's tabs and the view state kept for each of them.
 *
 * Filters, sorting and the search are kept per tab rather than shared, because the tabs show
 * different populations with different fields - a filter that makes sense for trial members is
 * meaningless on the manager tab. Switching tabs therefore restores what that tab last looked
 * like instead of carrying the previous tab's narrowing across. The columns follow the tab too,
 * through the table's own memory.
 *
 * @param fields      every profile field, filtered here down to the ones the active tab can show
 * @param assignments who each of those fields is put to, which is what "can show" now means
 */
export function useMemberListTabs(fields: Ref<ProfileField[]>, assignments: Ref<ProfileFieldAssignment[]>) {
  const { t } = useI18n()
  const audiences = fieldAudiences(assignments)

  const activeTab = ref<string>('ALL')

  const tabStates = ref<Record<string, DataTableState>>(
    Object.fromEntries(TAB_KEYS.map(key => [key, emptyTableState('name')])),
  )

  const currentTabState = computed<DataTableState>(() => tabStates.value[activeTab.value] ?? emptyTableState('name'))

  const tabs = computed(() => [
    { key: 'ALL', label: t('membersList.tabAll') },
    { key: StationUserType.TRIAL, label: t('membersList.tabTrial') },
    { key: StationUserType.MEMBER, label: t('membersList.tabMember') },
    { key: StationUserType.GUARDIAN, label: t('membersList.tabMemberManager') },
    { key: StationUserType.TEAM, label: t('membersList.tabTeam') },
    { key: StationUserType.MANAGER, label: t('membersList.tabManager') },
  ])

  /**
   * The fields the active tab may show. The "all" tab spans every kind of member; a tab of one kind
   * is limited to what that kind is asked. A question put only to a group never appears, because it
   * belongs to a group rather than to a kind of member.
   */
  const tabScopedFields = computed(() => {
    const roles = activeTab.value === 'ALL' ? EVERY_KIND : [activeTab.value]
    return fields.value.filter(f => audiences.isAskedOfAny(f.id, roles))
  })

  return {
    activeTab,
    tabStates,
    currentTabState,
    tabs,
    tabScopedFields,
    isAskedOf: audiences.isAskedOf,
  }
}
