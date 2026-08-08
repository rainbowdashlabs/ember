/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {ProfileFieldChange} from '@/api/profileFieldChanges'
import {useChangeAcknowledgement} from '@/composables/useChangeAcknowledgement'
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

const {
  acknowledgeComment,
  showCommentForChangeId,
  acknowledging,
  error,
  isAcknowledgedByMe,
  unacknowledgedCount: countUnacknowledged,
  acknowledgeChange,
  acknowledgeAll,
  toggleComment,
} = useChangeAcknowledgement(() => props.currentMemberId, () => emit('reload'))

const unacknowledgedCount = computed(() => countUnacknowledged(props.changes))

function acknowledgeAllChanges() {
  return acknowledgeAll(props.memberId, true)
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
