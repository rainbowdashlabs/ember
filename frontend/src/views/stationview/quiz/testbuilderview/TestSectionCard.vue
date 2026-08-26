/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionLabel from '@/components/typography/SectionLabel.vue'
import TestSectionSourceRow from './TestSectionSourceRow.vue'
import type { QuizCatalog, QuizCategory } from '@/api/quiz'

interface SourceDraft {
  key: string
  catalogId: number | null
  categoryId: number | null
  questionCount: number
}

interface SectionDraft {
  key: string
  title: string
  description: string
  sources: SourceDraft[]
}

const props = defineProps<{
  section: SectionDraft
  index: number
  catalogs: QuizCatalog[]
  getCategoriesForCatalog: (catalogId: number | null) => QuizCategory[]
  onCatalogChange: (source: SourceDraft, value: string | undefined) => void
}>()

const emit = defineEmits<{
  remove: []
  addSource: []
  removeSource: [sourceIndex: number]
}>()

const { t } = useI18n()
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionLabel>
          {{ t('quiz.sections.sectionNumber', { n: props.index + 1 }) }}
        </SectionLabel>
        <DeleteButton @click="emit('remove')" />
      </div>

      <TextInput v-model="props.section.title" :placeholder="t('quiz.sections.titlePlaceholder')" />
      <TextAreaInput v-model="props.section.description" :placeholder="t('quiz.sections.descriptionPlaceholder')" />

      <div class="space-y-3">
        <label class="text-xs text-(--text-muted) font-medium">{{ t('quiz.sections.sources') }}</label>

        <TestSectionSourceRow
            v-for="(source, srcIdx) in props.section.sources"
            :key="source.key"
            :catalog-id="source.catalogId"
            v-model:category-id="source.categoryId"
            v-model:question-count="source.questionCount"
            :catalogs="props.catalogs"
            :categories="props.getCategoriesForCatalog(source.catalogId)"
            @update:catalog-id="v => props.onCatalogChange(source, v !== null ? String(v) : undefined)"
            @remove="emit('removeSource', srcIdx)"
        />

        <SecondaryButton :icon="['fas', 'plus']" @click="emit('addSource')">
          {{ t('quiz.sections.addSource') }}
        </SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
