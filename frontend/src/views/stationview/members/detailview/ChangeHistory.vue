/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {ProfileFieldChange} from '@/api/types'
import {profileFieldChanges} from '@/api'
import {usePendingChanges} from '@/composables/usePendingChanges'

const props = defineProps<{
  memberId: number
  changes: ProfileFieldChange[]
  currentMemberId: number
}>()

const emit = defineEmits<{
  reload: []
}>()

const {t} = useI18n()
const {refresh: refreshBadge} = usePendingChanges()

const error = ref('')
const acknowledgeComment = ref('')
const showCommentForChangeId = ref<number | null>(null)
const acknowledging = ref(false)

const unacknowledgedCount = computed(() => {
  return props.changes.filter(c =>
      c.requiresAcknowledgement && !c.acknowledgements.some(a => a.acknowledgedBy === props.currentMemberId)
  ).length
})

function formatValue(val?: string): string {
  if (!val) return '–'
  try {
    return JSON.parse(val)
  } catch {
    return val
  }
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  return date.toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function isAcknowledgedByMe(change: ProfileFieldChange): boolean {
  return change.acknowledgements.some(a => a.acknowledgedBy === props.currentMemberId)
}

async function acknowledgeChange(changeId: number) {
  acknowledging.value = true
  error.value = ''
  try {
    const comment = showCommentForChangeId.value === changeId ? acknowledgeComment.value : undefined
    await profileFieldChanges.acknowledge(changeId, {comment})
    showCommentForChangeId.value = null
    acknowledgeComment.value = ''
    refreshBadge()
    emit('reload')
  } catch {
    error.value = t('common.error')
  } finally {
    acknowledging.value = false
  }
}

async function acknowledgeAllChanges() {
  acknowledging.value = true
  error.value = ''
  try {
    await profileFieldChanges.acknowledgeAll(props.memberId, {comment: acknowledgeComment.value || undefined})
    acknowledgeComment.value = ''
    refreshBadge()
    emit('reload')
  } catch {
    error.value = t('common.error')
  } finally {
    acknowledging.value = false
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
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <h3 class="text-sm font-semibold">
        <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-2"/>
        {{ t('memberDetail.changeHistory') }}
      </h3>
      <SuccessButton
          v-if="unacknowledgedCount > 0"
          :disabled="acknowledging"
          class="text-sm"
          @click="acknowledgeAllChanges"
      >
        <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1"/>
        {{ t('memberDetail.acknowledgeAll') }}
      </SuccessButton>
    </div>

    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-if="changes.length === 0" class="text-(--text-muted) text-sm py-2">
      {{ t('memberDetail.noChanges') }}
    </div>

    <div class="space-y-3">
      <div
          v-for="change in changes"
          :key="change.id"
          :class="change.requiresAcknowledgement && !isAcknowledgedByMe(change)
          ? 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 border-l-4 border-primary'
          : 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'"
          class="rounded-lg px-4 py-3 space-y-2"
      >
        <!-- Header -->
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div class="flex items-center gap-2 flex-wrap">
            <span class="font-semibold text-sm">{{ change.fieldName }}</span>
            <span class="text-xs text-(--text-muted)">{{ formatDate(change.changedAt) }}</span>
            <span class="text-xs text-(--text-muted)">
              {{ t('memberDetail.changedBy') }}: {{ change.changedByName }}
            </span>
          </div>
          <div v-if="change.requiresAcknowledgement" class="flex items-center gap-2">
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

        <!-- Acknowledgements list (only for notify changes) -->
        <div v-if="change.requiresAcknowledgement && change.acknowledgements.length > 0"
             class="text-xs text-(--text-muted) space-y-1">
          <div v-for="ack in change.acknowledgements" :key="ack.id" class="flex items-center gap-1">
            <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3 text-success"/>
            <span>{{ ack.acknowledgedByName }} ({{ formatDate(ack.acknowledgedAt) }})</span>
            <span v-if="ack.comment" class="italic ml-1">
              <font-awesome-icon :icon="['fas', 'comment']" class="mr-1"/>
              {{ ack.comment }}
            </span>
          </div>
        </div>

        <!-- Action buttons (only for notify changes) -->
        <div v-if="change.requiresAcknowledgement && !isAcknowledgedByMe(change)" class="flex items-center gap-2 pt-1">
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
    </div>
  </NeutralContainer>
</template>
