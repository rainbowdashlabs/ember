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
import ButtonRow from '@/components/button/ButtonRow.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {ChangeEntry} from '@/api/profileFieldChanges'
import type {EnrichedMemberChangeSummary} from '@/api/generated/schema'
import PendingChangeRow from './PendingChangeRow.vue'

const {t} = useI18n()

/**
 * One member's pending changes, folded. The header opens them wherever it is pressed, which is a
 * pointer's convenience; the member's name is the control a keyboard and a screen reader reach.
 */
const acknowledgeComment = defineModel<string>('acknowledgeComment', {required: true})

defineProps<{
  summary: EnrichedMemberChangeSummary
  expanded: boolean
  memberChanges: ChangeEntry[]
  loadingChanges: boolean
  acknowledging: boolean
  showCommentForChangeId: number | null
  isAcknowledgedByMe: (change: ChangeEntry) => boolean
  formatDate: (dateStr?: string) => string
}>()

const emit = defineEmits<{
  toggle: []
  acknowledgeAll: []
  acknowledgeOne: [changeId: number]
  toggleComment: [changeId: number]
  goToDetail: []
}>()
</script>

<template>
  <NeutralContainer>
    <div
        class="flex items-center justify-between flex-wrap gap-2 cursor-pointer"
        role="presentation"
        @click="emit('toggle')"
    >
      <div
          class="flex items-center gap-2"
          role="button"
          tabindex="0"
          :aria-expanded="expanded"
          @keydown.enter.prevent="emit('toggle')"
          @keydown.space.prevent="emit('toggle')"
      >
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
        <ButtonRow>
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
        </ButtonRow>
      </div>
    </div>

    <div v-if="expanded" class="mt-4 space-y-3">
      <Spinner v-if="loadingChanges" size="sm"/>
      <template v-else>
        <PendingChangeRow
            v-for="change in memberChanges"
            :key="change.id"
            v-model:acknowledge-comment="acknowledgeComment"
            :change="change"
            :is-acknowledged-by-me="isAcknowledgedByMe(change)"
            :show-comment="showCommentForChangeId === change.id"
            :acknowledging="acknowledging"
            :format-date="formatDate"
            @acknowledge="emit('acknowledgeOne', change.id)"
            @toggle-comment="emit('toggleComment', change.id)"
        />
      </template>
    </div>
  </NeutralContainer>
</template>
