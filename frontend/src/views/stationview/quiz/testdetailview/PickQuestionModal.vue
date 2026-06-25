/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import type { QuizQuestion } from '@/api/types'

const modelValue = defineModel<boolean>({required: true})
const search = defineModel<string>('search', {required: true})

defineProps<{
  questions: QuizQuestion[]
  questionTypeName: (q: QuizQuestion) => string
}>()

const emit = defineEmits<{
  pick: [questionId: number]
}>()

const { t } = useI18n()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('quiz.frozenQuestions.pickTitle') }}</SubHeader>
      <SearchInput v-model="search" :placeholder="t('quiz.frozenQuestions.searchPlaceholder')" />
      <div class="max-h-80 overflow-y-auto space-y-2">
        <div v-if="questions.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
          {{ t('quiz.frozenQuestions.noAvailable') }}
        </div>
        <NeutralContainer
            v-for="q in questions"
            :key="q.id"
            class="cursor-pointer hover:!border-primary transition-colors"
            @click="emit('pick', q.id)"
        >
          <div class="flex items-center gap-2">
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="text-sm font-medium">{{ q.title }}</span>
                <SecondaryBadge>{{ questionTypeName(q) }}</SecondaryBadge>
                <InfoBadge>{{ q.points }} {{ t('quiz.points') }}</InfoBadge>
              </div>
            </div>
          </div>
        </NeutralContainer>
      </div>
      <div class="flex justify-end">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
