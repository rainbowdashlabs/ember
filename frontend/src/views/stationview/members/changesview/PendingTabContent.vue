/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'
import type {MemberChangeSummary, ProfileFieldChange} from '@/api/types'
import PendingMemberCard from './PendingMemberCard.vue'

const {t} = useI18n()

const acknowledgeComment = defineModel<string>('acknowledgeComment', {required: true})

defineProps<{
  loading: boolean
  summaries: MemberChangeSummary[]
  expandedMemberId: number | null
  memberChanges: ProfileFieldChange[]
  loadingChanges: boolean
  acknowledging: boolean
  showCommentForChangeId: number | null
  isAcknowledgedByMe: (change: ProfileFieldChange) => boolean
  formatDate: (dateStr?: string) => string
}>()

const emit = defineEmits<{
  toggle: [memberId: number]
  acknowledgeAll: [memberId: number]
  acknowledgeOne: [changeId: number]
  toggleComment: [changeId: number]
  goToDetail: [memberId: number]
}>()
</script>

<template>
  <Spinner v-if="loading" size="lg"/>
  <template v-if="!loading">
    <div v-if="summaries.length === 0" class="text-(--text-muted) text-sm py-4">
      {{ t('memberChanges.noChanges') }}
    </div>
    <div class="space-y-3">
      <PendingMemberCard
          v-for="summary in summaries"
          :key="summary.memberId"
          v-model:acknowledge-comment="acknowledgeComment"
          :summary="summary"
          :expanded="expandedMemberId === summary.memberId"
          :member-changes="memberChanges"
          :loading-changes="loadingChanges"
          :acknowledging="acknowledging"
          :show-comment-for-change-id="showCommentForChangeId"
          :is-acknowledged-by-me="isAcknowledgedByMe"
          :format-date="formatDate"
          @toggle="emit('toggle', summary.memberId)"
          @acknowledge-all="emit('acknowledgeAll', summary.memberId)"
          @acknowledge-one="(id) => emit('acknowledgeOne', id)"
          @toggle-comment="(id) => emit('toggleComment', id)"
          @go-to-detail="emit('goToDetail', summary.memberId)"
      />
    </div>
  </template>
</template>
