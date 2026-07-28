/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import {StationUserType} from '@/api/types'
import {inventoryCheck} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {byDate, byValue, useSortable} from '@/composables/useSortable'
import CheckOverviewTabs from './checkoverviewview/CheckOverviewTabs.vue'
import MemberCheckTable from './checkoverviewview/MemberCheckTable.vue'
import MemberCheckCardList from './checkoverviewview/MemberCheckCardList.vue'
import {memberName} from './checkoverviewview/memberHelpers'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()

const {config: members, loading, error} = useConfigPanel<MemberCheckSummary[]>({
  initial: [],
  fetch: () => inventoryCheck.getCheckOverview(),
})
const activeTab = ref<'team' | 'member'>('member')

const currentMemberId = computed(() => sessionInfo.value?.member?.id)

const filteredMembers = computed(() => members.value.filter(m => {
  const ut = m.userType ?? ''
  if (ut === StationUserType.GUARDIAN) return false
  if (activeTab.value === 'team') {
    return ut === StationUserType.TEAM || ut === StationUserType.MANAGER
  }
  return ut === StationUserType.MEMBER
}))

const {sortKey: sortBy, sorted: sortedMembers} = useSortable<MemberCheckSummary, 'name' | 'lastChecked'>({
  items: filteredMembers,
  initialKey: 'name',
  comparators: {
    name: byValue(memberName),
    lastChecked: byDate(m => m.lastCheckedAt, {nulls: 'first'}),
  },
  fallback: byValue(memberName),
})

function startCheck(memberId: number) {
  router.push({name: 'inventory-check-member', params: {memberId}, query: {teamOnly: activeTab.value === 'team' ? 'true' : 'false'}})
}

function viewLastCheck(member: MemberCheckSummary) {
  router.push({name: 'inventory-check-result', params: {memberId: member.memberId}, query: {name: memberName(member)}})
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-checks.title')"
      :subtitle="t('pages.inventory-checks.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && !error">
        <CheckOverviewTabs v-model:active-tab="activeTab" v-model:sort-by="sortBy"/>

        <EmptyState v-if="sortedMembers.length === 0">{{ t('inventory.check.noMembers') }}</EmptyState>

        <template v-else>
          <MemberCheckTable
              :members="sortedMembers"
              :current-member-id="currentMemberId"
              @start-check="startCheck"
              @view-last-check="viewLastCheck"
          />
          <MemberCheckCardList
              :members="sortedMembers"
              :current-member-id="currentMemberId"
              @start-check="startCheck"
              @view-last-check="viewLastCheck"
          />
        </template>
      </template>
    </div>
  </ViewContent>
</template>
