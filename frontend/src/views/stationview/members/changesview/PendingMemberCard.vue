/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {MemberChangeSummary, ProfileFieldChange} from '@/api/types'
import PendingChangeRow from './PendingChangeRow.vue'

const {t} = useI18n()

defineProps<{
  summary: MemberChangeSummary
  expanded: boolean
  memberChanges: ProfileFieldChange[]
  loadingChanges: boolean
  acknowledging: boolean
  showCommentForChangeId: number | null
  acknowledgeComment: string
  isAcknowledgedByMe: (change: ProfileFieldChange) => boolean
  formatDate: (dateStr?: string) => string
}>()

const emit = defineEmits<{
  toggle: []
  acknowledgeAll: []
  acknowledgeOne: [changeId: number]
  toggleComment: [changeId: number]
  goToDetail: []
  'update:acknowledgeComment': [value: string]
}>()
</script>

<template>
  <NeutralContainer>
    <div
        class="flex items-center justify-between flex-wrap gap-2 cursor-pointer"
        @click="emit('toggle')"
    >
      <div class="flex items-center gap-2">
        <font-awesome-icon
            :icon="['fas', expanded ? 'chevron-down' : 'chevron-right']"
            class="h-3 w-3 text-(--text-muted)"
        />
        <div>
          <span class="font-semibold text-sm"><MemberName :identity="summary.identity"/></span>
          <p class="text-xs text-(--text-muted)">
            {{ t('memberChanges.lastChange') }}: {{ formatDate(summary.latestChange) }}
          </p>
        </div>
      </div>
      <div class="flex items-center gap-2">
        <ErrorBadge>
          {{ summary.pendingCount }} {{ t('memberChanges.pending') }}
        </ErrorBadge>
        <SuccessButton
            :disabled="acknowledging"
            class="text-xs"
            @click.stop="emit('acknowledgeAll')"
        >
          <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1"/>
          {{ t('memberDetail.acknowledgeAll') }}
        </SuccessButton>
        <SecondaryButton :icon="['fas', 'user']" @click.stop="emit('goToDetail')">
          {{ t('memberChanges.toProfile') }}
        </SecondaryButton>
      </div>
    </div>

    <div v-if="expanded" class="mt-4 space-y-3">
      <Spinner v-if="loadingChanges" size="sm"/>
      <template v-else>
        <PendingChangeRow
            v-for="change in memberChanges"
            :key="change.id"
            :change="change"
            :is-acknowledged-by-me="isAcknowledgedByMe(change)"
            :show-comment="showCommentForChangeId === change.id"
            :acknowledging="acknowledging"
            :acknowledge-comment="acknowledgeComment"
            :format-date="formatDate"
            @acknowledge="emit('acknowledgeOne', change.id)"
            @toggle-comment="emit('toggleComment', change.id)"
            @update:acknowledge-comment="(v) => emit('update:acknowledgeComment', v)"
        />
      </template>
    </div>
  </NeutralContainer>
</template>
