/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import {StationUserType} from '@/api/types'
import {inventoryCheck} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfigPanel} from '@/composables/useConfigPanel'
import CheckOverviewTabs from './checkoverviewview/CheckOverviewTabs.vue'
import MemberCheckTable from './checkoverviewview/MemberCheckTable.vue'
import SelfCheckPanel from './checkoverviewview/SelfCheckPanel.vue'
import {memberName} from './checkoverviewview/memberHelpers'

const routes = useInventoryRoutes()

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()

const {config: members, loading, failure} = useConfigPanel<MemberCheckSummary[]>({
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

function startCheck(memberId: number) {
  router.push({name: routes.checkMember, params: {memberId}, query: {teamOnly: activeTab.value === 'team' ? 'true' : 'false'}})
}

function viewLastCheck(member: MemberCheckSummary) {
  router.push({name: routes.checkResult, params: {memberId: member.memberId}, query: {name: memberName(member)}})
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-checks.title')"
      :subtitle="t('pages.inventory-checks.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <template v-if="!loading && !failure">
        <SelfCheckPanel
            v-if="routes.selfCheckReview"
            :members="filteredMembers"
            :review-route="routes.selfCheckReview"
        />

        <CheckOverviewTabs v-model:active-tab="activeTab"/>

        <EmptyState v-if="filteredMembers.length === 0">{{ t('inventory.check.noMembers') }}</EmptyState>

        <MemberCheckTable
            v-else
            :current-member-id="currentMemberId"
            :members="filteredMembers"
            @start-check="startCheck"
            @view-last-check="viewLastCheck"
        />
      </template>
    </div>
  </ViewContent>
</template>
