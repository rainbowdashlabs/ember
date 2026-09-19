/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

/**
 * A column's title with its sort and filter controls beside it.
 *
 * <p>Both are real buttons so a column can be sorted and filtered from the keyboard. The filter
 * stops its click, because a header is often inside something that sorts when clicked.
 */
defineProps<{
  label: string
  sortIcon?: string
  hasFilter?: boolean
  showSort?: boolean
  showFilter?: boolean
  sortActive?: boolean
}>()

const emit = defineEmits<{
  sort: []
  filter: []
}>()

const {t} = useI18n()
</script>

<template>
  <span class="inline-flex items-center gap-0.5">
    {{ label }}
    <IconButton
        v-if="showSort && sortIcon"
        :class="sortActive ? 'text-primary' : 'text-(--text-muted) hover:text-primary'"
        :icon="['fas', sortIcon]"
        :label="t('tableFilter.sortBy', {column: label})"
        class="-my-2 p-1!"
        data-testid="column-sort"
        @click="emit('sort')"
    />
    <IconButton
        v-if="showFilter"
        :class="hasFilter ? 'text-primary' : 'text-(--text-muted) hover:text-primary'"
        :icon="['fas', 'filter']"
        :label="t('tableFilter.by', {column: label})"
        class="-my-2 p-1!"
        data-testid="column-filter-open"
        @click.stop="emit('filter')"
    />
  </span>
</template>
