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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {MemberCheckSummary} from '@/api/types'
import {StationUserType} from '@/api/types'
import {inventoryCheck} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfigPanel} from '@/composables/useConfigPanel'
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
const sortBy = ref<'name' | 'lastChecked'>('name')

const currentMemberId = computed(() => sessionInfo.value?.member?.id)

const filteredMembers = computed(() => {
  return members.value.filter(m => {
    const ut = m.userType ?? ''
    if (ut === StationUserType.GUARDIAN) return false
    if (activeTab.value === 'team') {
      return ut === StationUserType.TEAM || ut === StationUserType.MANAGER
    } else {
      return ut === StationUserType.MEMBER
    }
  }).sort((a, b) => {
    if (sortBy.value === 'lastChecked') {
      if (!a.lastCheckedAt && b.lastCheckedAt) return -1
      if (a.lastCheckedAt && !b.lastCheckedAt) return 1
      if (a.lastCheckedAt && b.lastCheckedAt) {
        return new Date(a.lastCheckedAt).getTime() - new Date(b.lastCheckedAt).getTime()
      }
    }
    return memberName(a).localeCompare(memberName(b), 'de')
  })
})

function startCheck(memberId: number) {
  router.push({name: 'inventory-check-member', params: {memberId}, query: {teamOnly: activeTab.value === 'team' ? 'true' : 'false'}})
}

function viewLastCheck(member: MemberCheckSummary) {
  router.push({name: 'inventory-check-result', params: {memberId: member.memberId}, query: {name: memberName(member)}})
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('inventory.check.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && !error">
        <CheckOverviewTabs v-model:active-tab="activeTab" v-model:sort-by="sortBy"/>

        <EmptyState v-if="filteredMembers.length === 0">{{ t('inventory.check.noMembers') }}</EmptyState>

        <template v-else>
          <MemberCheckTable
              :members="filteredMembers"
              :current-member-id="currentMemberId"
              @start-check="startCheck"
              @view-last-check="viewLastCheck"
          />
          <MemberCheckCardList
              :members="filteredMembers"
              :current-member-id="currentMemberId"
              @start-check="startCheck"
              @view-last-check="viewLastCheck"
          />
        </template>
      </template>
    </div>
  </ViewContent>
</template>
