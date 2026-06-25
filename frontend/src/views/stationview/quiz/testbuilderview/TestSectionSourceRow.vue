/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import type { QuizCatalog, QuizCategory } from '@/api/types'

const catalogId = defineModel<number | null>('catalogId', {required: true})
const categoryId = defineModel<number | null>('categoryId', {required: true})
const questionCount = defineModel<number>('questionCount', {required: true})

const props = defineProps<{
  catalogs: QuizCatalog[]
  categories: QuizCategory[]
}>()

defineEmits<{
  remove: []
}>()

const { t } = useI18n()

function onCatalogChange(value: string | undefined) {
  catalogId.value = value ? Number(value) : null
}

function onCategoryChange(value: string | undefined) {
  categoryId.value = value ? Number(value) : null
}

function onQuestionCountChange(value: number | undefined) {
  questionCount.value = value ?? 0
}
</script>

<template>
  <div class="flex flex-col sm:flex-row gap-2 items-start sm:items-center p-3 rounded border border-bg-light-accent dark:border-bg-dark-accent">
    <SelectInput
        :model-value="String(catalogId ?? '')"
        class="flex-1"
        @update:model-value="onCatalogChange"
    >
      <option value="">{{ t('quiz.sections.selectCatalog') }}</option>
      <option v-for="catalog in props.catalogs" :key="catalog.id" :value="String(catalog.id)">
        {{ catalog.name }}
      </option>
    </SelectInput>

    <SelectInput
        v-if="props.categories.length > 0"
        :model-value="String(categoryId ?? '')"
        class="flex-1"
        @update:model-value="onCategoryChange"
    >
      <option value="">{{ t('quiz.sections.allCategories') }}</option>
      <option v-for="cat in props.categories" :key="cat.id" :value="String(cat.id)">
        {{ cat.name }}
      </option>
    </SelectInput>

    <div class="flex items-center gap-2">
      <label class="text-xs text-(--text-muted) whitespace-nowrap">{{ t('quiz.sections.questionCount') }}</label>
      <NumberInput :model-value="questionCount" class="w-20" @update:model-value="onQuestionCountChange" />
    </div>

    <DeleteButton @click="$emit('remove')" />
  </div>
</template>
