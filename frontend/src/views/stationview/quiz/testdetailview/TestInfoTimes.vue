/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import { QuizTestStatus } from '@/api/types'
import type { QuizTestDetail } from '@/api/types'

const editStartAt = defineModel<string>('editStartAt', {required: true})
const editEndAt = defineModel<string>('editEndAt', {required: true})

defineProps<{
  test: QuizTestDetail['test']
  canConfigure: boolean
}>()

const emit = defineEmits<{
  'mark-dirty': []
}>()

const { t } = useI18n()

function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.toLocaleDateString()} ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
}

function onStart(v: string) {
  editStartAt.value = v
  emit('mark-dirty')
}

function onEnd(v: string) {
  editEndAt.value = v
  emit('mark-dirty')
}
</script>

<template>
  <div v-if="canConfigure && test.status !== QuizTestStatus.CLOSED">
    <FieldLabel hint class="mb-1">{{ t('quiz.tests.startAt') }}</FieldLabel>
    <DateTimeInput :model-value="editStartAt" @update:model-value="onStart" />
  </div>
  <div v-else>
    <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.startAt') }}</span>
    <span>{{ formatDateTime(test.startAt) }}</span>
  </div>
  <div v-if="canConfigure && test.status !== QuizTestStatus.CLOSED">
    <FieldLabel hint class="mb-1">{{ t('quiz.tests.endAt') }}</FieldLabel>
    <DateTimeInput :model-value="editEndAt" @update:model-value="onEnd" />
  </div>
  <div v-else>
    <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.endAt') }}</span>
    <span>{{ formatDateTime(test.endAt) }}</span>
  </div>
</template>
