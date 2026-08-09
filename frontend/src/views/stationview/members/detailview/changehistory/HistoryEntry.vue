/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import type {ProfileFieldChange} from '@/api/profileFieldChanges'
import ChangeValueDiff from '../../changesview/ChangeValueDiff.vue'
import AcknowledgementList from '../../changesview/AcknowledgementList.vue'

const {t} = useI18n()

const props = defineProps<{
  change: ProfileFieldChange
  acknowledgedByMe: boolean
  acknowledging: boolean
  commentOpen: boolean
  commentValue: string
  formatDate: (dateStr?: string) => string
}>()

const emit = defineEmits<{
  (e: 'acknowledge'): void
  (e: 'toggle-comment'): void
  (e: 'update:commentValue', v: string): void
}>()

function updateComment(v: string | undefined) {
  emit('update:commentValue', v ?? '')
}
</script>

<template>
  <div
      :class="change.requiresAcknowledgement && !acknowledgedByMe
        ? 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 border-l-4 border-primary'
        : 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'"
      class="rounded-lg px-4 py-3 space-y-2"
  >
    <div class="flex items-center justify-between flex-wrap gap-2">
      <div class="flex items-center gap-2 flex-wrap">
        <span class="font-semibold text-sm">{{ change.fieldName }}</span>
        <span class="text-xs text-(--text-muted)">{{ formatDate(change.changedAt) }}</span>
        <span class="text-xs text-(--text-muted)">
          {{ t('memberDetail.changedBy') }}: {{ change.changedByName }}
        </span>
      </div>
      <div v-if="change.requiresAcknowledgement" class="flex items-center gap-2">
        <SuccessBadge v-if="acknowledgedByMe">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
          {{ t('memberDetail.acknowledged') }}
        </SuccessBadge>
        <ErrorBadge v-else>
          {{ t('memberDetail.notAcknowledged') }}
        </ErrorBadge>
      </div>
    </div>

    <ChangeValueDiff :old-value="change.oldValue" :new-value="change.newValue"/>

    <AcknowledgementList
        v-if="change.requiresAcknowledgement"
        :acknowledgements="change.acknowledgements"
        :format-date="formatDate"
    />

    <div v-if="change.requiresAcknowledgement && !acknowledgedByMe" class="flex items-center gap-2 pt-1">
      <PrimaryButton :icon="['fas', 'check']" :disabled="acknowledging" @click="emit('acknowledge')">
        {{ t('memberDetail.acknowledge') }}
      </PrimaryButton>
      <SecondaryButton :icon="['fas', 'comment']" @click="emit('toggle-comment')">
        {{ t('memberDetail.acknowledgeWithComment') }}
      </SecondaryButton>
    </div>

    <div v-if="commentOpen" class="space-y-2 pt-1">
      <TextAreaInput
          :model-value="props.commentValue"
          :placeholder="t('memberDetail.commentPlaceholder')"
          class="text-sm"
          @update:model-value="updateComment"
      />
      <PrimaryButton :disabled="acknowledging" @click="emit('acknowledge')">
        {{ t('memberDetail.submitAcknowledge') }}
      </PrimaryButton>
    </div>
  </div>
</template>
