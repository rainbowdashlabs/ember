/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {ProfileFieldChange} from '@/api/types'
import {profileFieldChanges} from '@/api'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncAction} from '@/composables/useAsyncAction'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import HistoryEntry from './changehistory/HistoryEntry.vue'
import {formatDateTime} from '@/util/format'

const props = defineProps<{
  memberId: number
  changes: ProfileFieldChange[]
  currentMemberId: number
}>()

const emit = defineEmits<{
  reload: []
}>()

const {t} = useI18n()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const acknowledgeComment = ref('')
const showCommentForChangeId = ref<number | null>(null)

const {running: acknowledging, error, run: runAcknowledge} = useAsyncAction(
    async (work: () => Promise<void>) => {
      await work()
      refreshSidebarCounts()
      emit('reload')
    },
    {formatError: () => t('common.error')},
)

const unacknowledgedCount = computed(() => {
  return props.changes.filter(c =>
      c.requiresAcknowledgement && !c.acknowledgements.some(a => a.acknowledgedBy === props.currentMemberId)
  ).length
})

function isAcknowledgedByMe(change: ProfileFieldChange): boolean {
  return change.acknowledgements.some(a => a.acknowledgedBy === props.currentMemberId)
}

function acknowledgeChange(changeId: number) {
  return runAcknowledge(async () => {
    const comment = showCommentForChangeId.value === changeId ? acknowledgeComment.value : undefined
    await profileFieldChanges.acknowledge(changeId, {comment})
    showCommentForChangeId.value = null
    acknowledgeComment.value = ''
  })
}

function acknowledgeAllChanges() {
  return runAcknowledge(async () => {
    await profileFieldChanges.acknowledgeAll(props.memberId, {comment: acknowledgeComment.value || undefined})
    acknowledgeComment.value = ''
  })
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
      <SubHeader class="text-sm">
        <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-2"/>
        {{ t('memberDetail.changeHistory') }}
      </SubHeader>
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

    <MutedText tag="div" size="sm" class="py-2" v-if="changes.length === 0">
      {{ t('memberDetail.noChanges') }}
    </MutedText>

    <div class="space-y-3">
      <HistoryEntry
          v-for="change in changes"
          :key="change.id"
          :change="change"
          :acknowledged-by-me="isAcknowledgedByMe(change)"
          :acknowledging="acknowledging"
          :comment-open="showCommentForChangeId === change.id"
          v-model:comment-value="acknowledgeComment"
          :format-date="formatDateTime"
          @acknowledge="acknowledgeChange(change.id)"
          @toggle-comment="toggleComment(change.id)"
      />
    </div>
  </NeutralContainer>
</template>
