/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import type {MemberChangeSummary, ProfileFieldChange} from '@/api/types'
import {profileFieldChanges} from '@/api'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import PendingTabContent from './changesview/PendingTabContent.vue'
import HistoryTabContent from './changesview/HistoryTabContent.vue'
import {formatDateTime} from '@/util/format'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const activeTab = ref('pending')
const tabs = [
  {key: 'pending', label: t('memberChanges.tabPending')},
  {key: 'history', label: t('memberChanges.tabHistory')},
]

const summaries = ref<MemberChangeSummary[]>([])
const expandedMemberId = ref<number | null>(null)
const memberChanges = ref<ProfileFieldChange[]>([])
const loadingChanges = ref(false)
const acknowledging = ref(false)
const acknowledgeComment = ref('')
const showCommentForChangeId = ref<number | null>(null)

const historyChanges = ref<ProfileFieldChange[]>([])
const historyTotal = ref(0)
const historyOffset = ref(0)
const historyLimit = 20
const loadingHistory = ref(false)

const currentMemberId = () => sessionInfo.value?.member?.id ?? 0

const {loading, error} = useAsyncLoader(async () => {
  summaries.value = await profileFieldChanges.getPendingSummary()
})

async function toggleMember(memberId: number) {
  if (expandedMemberId.value === memberId) {
    expandedMemberId.value = null
    memberChanges.value = []
    return
  }
  expandedMemberId.value = memberId
  loadingChanges.value = true
  try {
    const allChanges = await profileFieldChanges.getChanges(memberId)
    memberChanges.value = allChanges.filter(c => c.requiresAcknowledgement)
  } catch {
    error.value = t('common.error')
  } finally {
    loadingChanges.value = false
  }
}

function isAcknowledgedByMe(change: ProfileFieldChange): boolean {
  return change.acknowledgements.some(a => a.acknowledgedBy === currentMemberId())
}

async function acknowledgeChange(changeId: number) {
  acknowledging.value = true
  error.value = ''
  try {
    const comment = showCommentForChangeId.value === changeId ? acknowledgeComment.value : undefined
    await profileFieldChanges.acknowledge(changeId, {comment})
    showCommentForChangeId.value = null
    acknowledgeComment.value = ''
    await reloadExpanded()
  } catch {
    error.value = t('common.error')
  } finally {
    acknowledging.value = false
  }
}

async function acknowledgeAllForMember(memberId: number) {
  acknowledging.value = true
  error.value = ''
  try {
    await profileFieldChanges.acknowledgeAll(memberId, {})
    await reloadExpanded()
  } catch {
    error.value = t('common.error')
  } finally {
    acknowledging.value = false
  }
}

async function reloadExpanded() {
  summaries.value = await profileFieldChanges.getPendingSummary()
  refreshSidebarCounts()
  if (expandedMemberId.value != null) {
    const stillInList = summaries.value.some(s => s.memberId === expandedMemberId.value)
    if (stillInList) {
      const allChanges = await profileFieldChanges.getChanges(expandedMemberId.value)
      memberChanges.value = allChanges.filter(c => c.requiresAcknowledgement)
    } else {
      expandedMemberId.value = null
      memberChanges.value = []
    }
  }
}

function toggleComment(changeId: number) {
  if (showCommentForChangeId.value === changeId) {
    showCommentForChangeId.value = null
    acknowledgeComment.value = ''
  } else {
    showCommentForChangeId.value = changeId
    acknowledgeComment.value = ''
  }
}

function goToDetail(memberId: number) {
  router.push({name: 'members-detail', params: {id: memberId}})
}

async function loadHistory() {
  loadingHistory.value = true
  error.value = ''
  try {
    const res = await profileFieldChanges.getAllChanges(historyOffset.value, historyLimit)
    historyChanges.value = res.changes
    historyTotal.value = res.total
  } catch {
    error.value = t('common.error')
  } finally {
    loadingHistory.value = false
  }
}

function historyPrevPage() {
  historyOffset.value = Math.max(0, historyOffset.value - historyLimit)
  loadHistory()
}

function historyNextPage() {
  if (historyOffset.value + historyLimit < historyTotal.value) {
    historyOffset.value += historyLimit
    loadHistory()
  }
}

const historyPage = () => Math.floor(historyOffset.value / historyLimit) + 1
const historyTotalPages = () => Math.ceil(historyTotal.value / historyLimit)

function onTabChange(tab: string) {
  activeTab.value = tab
  if (tab === 'history' && historyChanges.value.length === 0) {
    loadHistory()
  }
}

</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <TabBar :tabs="tabs" :model-value="activeTab" @update:model-value="onTabChange"/>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <PendingTabContent
          v-if="activeTab === 'pending'"
          v-model:acknowledge-comment="acknowledgeComment"
          :loading="loading"
          :summaries="summaries"
          :expanded-member-id="expandedMemberId"
          :member-changes="memberChanges"
          :loading-changes="loadingChanges"
          :acknowledging="acknowledging"
          :show-comment-for-change-id="showCommentForChangeId"
          :is-acknowledged-by-me="isAcknowledgedByMe"
          :format-date="formatDateTime"
          @toggle="toggleMember"
          @acknowledge-all="acknowledgeAllForMember"
          @acknowledge-one="acknowledgeChange"
          @toggle-comment="toggleComment"
          @go-to-detail="goToDetail"
      />

      <HistoryTabContent
          v-if="activeTab === 'history'"
          :loading="loadingHistory"
          :changes="historyChanges"
          :offset="historyOffset"
          :limit="historyLimit"
          :total="historyTotal"
          :page="historyPage()"
          :total-pages="historyTotalPages()"
          :format-date="formatDateTime"
          @prev="historyPrevPage"
          @next="historyNextPage"
      />
    </div>
  </ViewContent>
</template>
