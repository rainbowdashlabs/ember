/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import DragList from '@/components/input/DragList.vue'
import TestSectionCard from './TestSectionCard.vue'
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
  sections: SectionDraft[]
  catalogs: QuizCatalog[]
  getCategoriesForCatalog: (catalogId: number | null) => QuizCategory[]
  onCatalogChange: (source: SourceDraft, value: string | undefined) => void
}>()

const emit = defineEmits<{
  addSection: []
  reorderSections: [fromIndex: number, toIndex: number]
  removeSection: [index: number]
  addSource: [section: SectionDraft]
  removeSource: [section: SectionDraft, sourceIndex: number]
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('quiz.sections.title') }}</SubHeader>
      <SecondaryButton :icon="['fas', 'plus']" @click="emit('addSection')">
        {{ t('quiz.sections.add') }}
      </SecondaryButton>
    </div>

    <EmptyState compact v-if="props.sections.length === 0">{{ t('quiz.sections.noSections') }}</EmptyState>

    <DragList
        :items="props.sections"
        :key-fn="(section) => section.key"
        class="space-y-4"
        @reorder="(from, to) => emit('reorderSections', from, to)"
    >
      <template #default="{item: section, index: sIdx}">
        <TestSectionCard
            :section="section"
            :index="sIdx"
            :catalogs="props.catalogs"
            :get-categories-for-catalog="props.getCategoriesForCatalog"
            :on-catalog-change="props.onCatalogChange"
            @remove="emit('removeSection', sIdx)"
            @add-source="emit('addSource', section)"
            @remove-source="srcIdx => emit('removeSource', section, srcIdx)"
        />
      </template>
    </DragList>
  </div>
</template>
