/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {PlaceholderCategoryBranch} from './placeholderTree'

/**
 * The categories of the picker in a row, the open one marked. Choosing the open one again closes it.
 */
defineProps<{
  categories: PlaceholderCategoryBranch[]
  /** The name of the open category. */
  active?: string
}>()

const emit = defineEmits<{
  toggle: [name: string]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap gap-1.5" role="group" :aria-label="t('documentTemplates.placeholderPicker.categories')">
    <SelectionToggleButton v-for="category in categories" :key="category.category" :selected="category.name === active"
                           :data-testid="`placeholder-category-${category.category}`" @toggle="emit('toggle', category.name)">
      {{ category.name }}
    </SelectionToggleButton>
  </div>
</template>
