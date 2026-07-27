/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { FormQuestionAnalytics, ProfileField } from '@/api/types'
import type { ExportFieldOption } from '@/composables/useExport'

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  questions: FormQuestionAnalytics[]
  fields: ProfileField[]
  selectedQuestionIds: Set<number>
  selectedFieldIds: Set<number>
}>()

const emit = defineEmits<{
  toggleQuestion: [id: number]
  toggleField: [id: number]
  selectQuestions: [ids: number[]]
  export: []
}>()

const { t } = useI18n()

const questionOptions = computed((): ExportFieldOption<number>[] =>
  props.questions.map(q => ({key: q.questionId, label: q.title})),
)

const fieldOptions = computed((): ExportFieldOption<number>[] =>
  props.fields.map(f => ({key: f.id, label: f.name ?? ''})),
)
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('forms.analytics.export') }}</SubHeader>

      <ExportFieldPicker
        bulk
        :label="t('forms.analytics.exportQuestions')"
        :options="questionOptions"
        :selected="selectedQuestionIds"
        @toggle="emit('toggleQuestion', $event)"
        @select="emit('selectQuestions', $event)"
      />

      <ExportFieldPicker
        :label="t('forms.analytics.exportFields')"
        :options="fieldOptions"
        :selected="selectedFieldIds"
        @toggle="emit('toggleField', $event)"
      />

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'file-csv']" :disabled="selectedQuestionIds.size === 0" @click="emit('export')">
          {{ t('forms.analytics.exportCsv') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
