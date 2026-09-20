/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { FormQuestionAnalytics } from '@/api/forms'
import type { ProfileField } from '@/api/profileFields'
import type { ExportFieldOption } from '@/composables/useExport'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import ExportSeparatorField from '@/components/documents/ExportSeparatorField.vue'
import type { ExportFormat, ExportSeparator } from '@/util/exportFormat'

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
  export: [format: ExportFormat, separator: ExportSeparator]
}>()

const { t } = useI18n()

const format = ref<ExportFormat>('csv')
const separator = ref<ExportSeparator>('semicolon')

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

      <div class="space-y-2">
        <FieldLabel>{{ t('exportFormat.format') }}</FieldLabel>
        <div class="flex items-center gap-4">
          <FieldLabel inline class="cursor-pointer">
            <RadioInput v-model="format" value="csv"/>
            {{ t('exportFormat.csv') }}
          </FieldLabel>
          <FieldLabel inline class="cursor-pointer">
            <RadioInput v-model="format" value="pdf"/>
            {{ t('exportFormat.pdf') }}
          </FieldLabel>
        </div>
      </div>

      <ExportSeparatorField v-if="format === 'csv'" v-model="separator"/>

      <ButtonRow pair align="end">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton
            :icon="['fas', 'download']"
            :disabled="selectedQuestionIds.size === 0"
            @click="emit('export', format, separator)"
        >
          {{ t('common.export') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
