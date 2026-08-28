/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import type {SortDirection} from '@/composables/useSortable'
import {sortIconFor} from '@/composables/useSortable'

/**
 * The heading of the member column, which is also where the table is put in order.
 *
 * <p>One column, two orders: a roster is read by first name and a list is checked off by surname,
 * and which of the two somebody wants depends on the paper in their other hand. Two named buttons
 * rather than one arrow, because an arrow on a column headed "Mitglied" cannot say which half of
 * the name it is about.
 */
defineProps<{
  activeKey: 'firstName' | 'lastName'
  direction: SortDirection
}>()

const emit = defineEmits<{
  (e: 'sort', key: 'firstName' | 'lastName'): void
}>()

const {t} = useI18n()
</script>

<template>
  <th class="py-1 pr-3 font-medium">
    <span class="inline-flex items-center gap-2">
      {{ t('inventory.intake.member') }}
      <button
          v-for="key in (['firstName', 'lastName'] as const)"
          :key="key"
          type="button"
          class="inline-flex items-center gap-1 text-xs font-normal"
          :class="activeKey === key ? 'text-primary' : 'text-(--text-muted) hover:text-primary'"
          :aria-pressed="activeKey === key"
          :data-testid="`intake-sort-${key}`"
          @click="emit('sort', key)"
      >
        {{ t(`inventory.intake.sortBy.${key}`) }}
        <font-awesome-icon :icon="['fas', sortIconFor(activeKey === key, direction)]"/>
      </button>
    </span>
  </th>
</template>
