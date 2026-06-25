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
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {ProfileFieldChange} from '@/api/types'
import ChangeValueDiff from './ChangeValueDiff.vue'
import AcknowledgementList from './AcknowledgementList.vue'

const {t} = useI18n()

const acknowledgeComment = defineModel<string>('acknowledgeComment', {required: true})

defineProps<{
  change: ProfileFieldChange
  isAcknowledgedByMe: boolean
  showComment: boolean
  acknowledging: boolean
  formatDate: (dateStr?: string) => string
}>()

const emit = defineEmits<{
  acknowledge: []
  toggleComment: []
}>()
</script>

<template>
  <div
      :class="isAcknowledgedByMe
        ? 'bg-bg-light-accent/20 dark:bg-bg-dark-accent/20'
        : 'bg-bg-light-accent/40 dark:bg-bg-dark-accent/40 border-l-4 border-primary'"
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
      <div class="flex items-center gap-2">
        <SuccessBadge v-if="isAcknowledgedByMe">
          <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
          {{ t('memberDetail.acknowledged') }}
        </SuccessBadge>
        <ErrorBadge v-else>
          {{ t('memberDetail.notAcknowledged') }}
        </ErrorBadge>
      </div>
    </div>

    <ChangeValueDiff :old-value="change.oldValue" :new-value="change.newValue"/>

    <AcknowledgementList :acknowledgements="change.acknowledgements" :format-date="formatDate"/>

    <div v-if="!isAcknowledgedByMe" class="flex items-center gap-2 pt-1">
      <PrimaryButton :icon="['fas', 'check']" :disabled="acknowledging" @click="emit('acknowledge')">
        {{ t('memberDetail.acknowledge') }}
      </PrimaryButton>
      <SecondaryButton :icon="['fas', 'comment']" @click="emit('toggleComment')">
        {{ t('memberDetail.acknowledgeWithComment') }}
      </SecondaryButton>
    </div>

    <div v-if="showComment" class="space-y-2 pt-1">
      <TextAreaInput
          v-model="acknowledgeComment"
          :placeholder="t('memberDetail.commentPlaceholder')"
          class="text-sm"
      />
      <PrimaryButton :disabled="acknowledging" @click="emit('acknowledge')">
        {{ t('memberDetail.submitAcknowledge') }}
      </PrimaryButton>
    </div>
  </div>
</template>
