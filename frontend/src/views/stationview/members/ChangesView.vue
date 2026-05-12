/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {MemberChangeSummary, ProfileFieldChange} from '@/api/types'
import {profileFieldChanges} from '@/api'
import {useSession} from '@/composables/useSession'
import {usePendingChanges} from '@/composables/usePendingChanges'

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()
const {refresh: refreshBadge} = usePendingChanges()

const summaries = ref<MemberChangeSummary[]>([])
const loading = ref(true)
const error = ref('')

// Expanded member state
const expandedMemberId = ref<number | null>(null)
const memberChanges = ref<ProfileFieldChange[]>([])
const loadingChanges = ref(false)

// Acknowledge state
const acknowledging = ref(false)
const acknowledgeComment = ref('')
const showCommentForChangeId = ref<number | null>(null)

const currentMemberId = () => sessionInfo.value?.member?.id ?? 0

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function formatValue(val?: string): string {
  if (!val) return '–'
  try {
    return JSON.parse(val)
  } catch {
    return val
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    summaries.value = await profileFieldChanges.getPendingSummary()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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
  refreshBadge()
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

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div v-if="summaries.length === 0" class="text-(--text-muted) text-sm py-4">
          {{ t('memberChanges.noChanges') }}
        </div>

        <div class="space-y-3">
          <NeutralContainer
              v-for="summary in summaries"
              :key="summary.memberId"
          >
            <!-- Member header -->
            <div
                class="flex items-center justify-between flex-wrap gap-3 cursor-pointer"
                @click="toggleMember(summary.memberId)"
            >
              <div class="flex items-center gap-3">
                <font-awesome-icon
                    :icon="['fas', expandedMemberId === summary.memberId ? 'chevron-down' : 'chevron-right']"
                    class="h-3 w-3 text-(--text-muted)"
                />
                <font-awesome-icon :icon="['fas', 'user']" class="h-4 w-4 text-(--text-muted)"/>
                <div>
                  <span class="font-semibold text-sm">{{ summary.memberName }}</span>
                  <p class="text-xs text-(--text-muted)">
                    {{ t('memberChanges.lastChange') }}: {{ formatDate(summary.latestChange) }}
                  </p>
                </div>
              </div>
              <div class="flex items-center gap-3">
                <ErrorBadge>
                  {{ summary.pendingCount }} {{ t('memberChanges.pending') }}
                </ErrorBadge>
                <SuccessButton
                    :disabled="acknowledging"
                    class="text-xs"
                    @click.stop="acknowledgeAllForMember(summary.memberId)"
                >
                  <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1"/>
                  {{ t('memberDetail.acknowledgeAll') }}
                </SuccessButton>
                <SecondaryButton class="text-xs" @click.stop="goToDetail(summary.memberId)">
                  <font-awesome-icon :icon="['fas', 'user']" class="mr-1"/>
                  {{ t('memberChanges.toProfile') }}
                </SecondaryButton>
              </div>
            </div>

            <!-- Expanded changes -->
            <div v-if="expandedMemberId === summary.memberId" class="mt-4 space-y-3">
              <Spinner v-if="loadingChanges" size="sm"/>
              <template v-else>
                <div
                    v-for="change in memberChanges"
                    :key="change.id"
                    :class="isAcknowledgedByMe(change)
                    ? 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'
                    : 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 border-l-4 border-primary'"
                    class="rounded-lg px-4 py-3 space-y-2"
                >
                  <!-- Change header -->
                  <div class="flex items-center justify-between flex-wrap gap-2">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="font-semibold text-sm">{{ change.fieldName }}</span>
                      <span class="text-xs text-(--text-muted)">{{ formatDate(change.changedAt) }}</span>
                      <span class="text-xs text-(--text-muted)">
                        {{ t('memberDetail.changedBy') }}: {{ change.changedByName }}
                      </span>
                    </div>
                    <div class="flex items-center gap-2">
                      <SuccessBadge v-if="isAcknowledgedByMe(change)">
                        <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                        {{ t('memberDetail.acknowledged') }}
                      </SuccessBadge>
                      <ErrorBadge v-else>
                        {{ t('memberDetail.notAcknowledged') }}
                      </ErrorBadge>
                    </div>
                  </div>

                  <!-- Values -->
                  <div class="flex items-center gap-3 text-sm">
                    <span class="text-(--text-muted)">{{ formatValue(change.oldValue) }}</span>
                    <font-awesome-icon :icon="['fas', 'chevron-right']" class="h-3 w-3 text-(--text-muted)"/>
                    <span class="font-medium">{{ formatValue(change.newValue) }}</span>
                  </div>

                  <!-- Acknowledgements list -->
                  <div v-if="change.acknowledgements.length > 0" class="text-xs text-(--text-muted) space-y-1">
                    <div v-for="ack in change.acknowledgements" :key="ack.id" class="flex items-center gap-1">
                      <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3 text-success"/>
                      <span>{{ ack.acknowledgedByName }} ({{ formatDate(ack.acknowledgedAt) }})</span>
                      <span v-if="ack.comment" class="italic ml-1">
                        <font-awesome-icon :icon="['fas', 'comment']" class="mr-1"/>
                        {{ ack.comment }}
                      </span>
                    </div>
                  </div>

                  <!-- Action buttons -->
                  <div v-if="!isAcknowledgedByMe(change)" class="flex items-center gap-2 pt-1">
                    <PrimaryButton :disabled="acknowledging" class="text-xs" @click="acknowledgeChange(change.id)">
                      <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                      {{ t('memberDetail.acknowledge') }}
                    </PrimaryButton>
                    <SecondaryButton class="text-xs" @click="toggleComment(change.id)">
                      <font-awesome-icon :icon="['fas', 'comment']" class="mr-1"/>
                      {{ t('memberDetail.acknowledgeWithComment') }}
                    </SecondaryButton>
                  </div>

                  <!-- Comment input -->
                  <div v-if="showCommentForChangeId === change.id" class="space-y-2 pt-1">
                    <TextAreaInput
                        v-model="acknowledgeComment"
                        :placeholder="t('memberDetail.commentPlaceholder')"
                        class="text-sm"
                    />
                    <PrimaryButton :disabled="acknowledging" class="text-xs" @click="acknowledgeChange(change.id)">
                      {{ t('memberDetail.submitAcknowledge') }}
                    </PrimaryButton>
                  </div>
                </div>
              </template>
            </div>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
