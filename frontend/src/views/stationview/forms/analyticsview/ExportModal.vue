/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { FormQuestionAnalytics, ProfileField } from '@/api/types'

const modelValue = defineModel<boolean>({required: true})

defineProps<{
  questions: FormQuestionAnalytics[]
  fields: ProfileField[]
  selectedQuestionIds: Set<number>
  selectedFieldIds: Set<number>
}>()

const emit = defineEmits<{
  'toggle-question': [id: number]
  'toggle-field': [id: number]
  'select-all-questions': []
  'select-no-questions': []
  export: []
}>()

const { t } = useI18n()

function close() {
  modelValue.value = false
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('forms.analytics.export') }}</SubHeader>

      <div class="space-y-2">
        <div class="flex items-center justify-between">
          <label class="text-sm font-medium">{{ t('forms.analytics.exportQuestions') }}</label>
          <div class="flex gap-2 text-xs">
            <SecondaryButton class="text-xs !px-2 !py-0.5" @click="emit('select-all-questions')">
              {{ t('forms.analytics.selectAll') }}
            </SecondaryButton>
            <SecondaryButton class="text-xs !px-2 !py-0.5" @click="emit('select-no-questions')">
              {{ t('forms.analytics.selectNone') }}
            </SecondaryButton>
          </div>
        </div>
        <div class="max-h-40 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
          <div v-for="q in questions" :key="q.questionId" class="py-0.5">
            <SelectionToggleButton :selected="selectedQuestionIds.has(q.questionId)"
                                   @toggle="emit('toggle-question', q.questionId)">
              {{ q.title }}
            </SelectionToggleButton>
          </div>
        </div>
      </div>

      <div class="space-y-2">
        <label class="text-sm font-medium">{{ t('forms.analytics.exportFields') }}</label>
        <div class="max-h-40 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
          <div v-for="f in fields" :key="f.id" class="py-0.5">
            <SelectionToggleButton :selected="selectedFieldIds.has(f.id)" @toggle="emit('toggle-field', f.id)">
              {{ f.name }}
            </SelectionToggleButton>
          </div>
          <p v-if="fields.length === 0" class="text-xs text-(--text-muted)">–</p>
        </div>
      </div>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="close">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="selectedQuestionIds.size === 0" @click="emit('export')">
          {{ t('forms.analytics.exportCsv') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
