/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {EventCategory} from '@/api/generated/schema'

/**
 * The categories a calendar feed leaves out, one toggle each. A ticked category is left out; with
 * none ticked the feed carries every category.
 */
const props = defineProps<{
  categories: EventCategory[]
  hint?: string
}>()

const hidden = defineModel<number[]>({required: true})

const {t} = useI18n()

function toggle(id: number) {
  hidden.value = hidden.value.includes(id) ? hidden.value.filter(other => other !== id) : [...hidden.value, id]
}
</script>

<template>
  <div class="space-y-1">
    <FieldLabel>{{ t('calendarFeeds.hiddenCategories') }}</FieldLabel>
    <MutedText v-if="props.categories.length === 0" tag="p" size="sm">{{ t('calendarFeeds.noCategories') }}</MutedText>
    <div v-else class="flex flex-wrap gap-2">
      <SelectionToggleButton
          v-for="category in props.categories"
          :key="category.id"
          :selected="hidden.includes(category.id)"
          :data-testid="`feed-category-${category.id}`"
          @toggle="toggle(category.id)"
      >{{ category.name }}</SelectionToggleButton>
    </div>
    <MutedText tag="p" size="sm">{{ props.hint ?? t('calendarFeeds.hiddenCategoriesHint') }}</MutedText>
  </div>
</template>
