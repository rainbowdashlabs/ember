/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import DateFilterTreeRow from './DateFilterTreeRow.vue'
import {
  buildDateTree,
  isTreeChecked,
  isTreeIndeterminate,
  toggleTreeToken,
} from '@/util/dateFilter'
import {formatDate} from '@/util/format'

/**
 * The year/month/day checkmark tree of a date filter.
 *
 * <p>The model is the prefix token list; flipping a row rewrites it through
 * {@link toggleTreeToken}, so a whole year stays one token and unchecking one day under it
 * unfolds only what the day leaves behind.
 */
const props = defineProps<{
  /** The column's distinct raw values; unreadable ones simply do not appear. */
  values: string[]
}>()

const prefixes = defineModel<string[]>({required: true})

const {t} = useI18n()

const tree = computed(() => buildDateTree(props.values))

const expanded = ref<Set<string>>(new Set())

function toggleExpand(token: string) {
  const next = new Set(expanded.value)
  if (next.has(token)) { next.delete(token) } else { next.add(token) }
  expanded.value = next
}

function toggle(token: string) {
  prefixes.value = toggleTreeToken(prefixes.value, token, tree.value)
}

function monthLabel(monthToken: string): string {
  const month = Number(monthToken.slice(5, 7))
  return new Date(2000, month - 1, 1).toLocaleDateString('de-DE', {month: 'long'})
}
</script>

<template>
  <div>
    <template v-for="year in tree" :key="year.year">
      <DateFilterTreeRow
          :label="year.year"
          :checked="isTreeChecked(prefixes, year.year)"
          :indeterminate="isTreeIndeterminate(prefixes, year.year)"
          :depth="0"
          expandable
          :expanded="expanded.has(year.year)"
          :expand-label="t('tableFilter.expandYear')"
          @toggle="toggle(year.year)"
          @expand="toggleExpand(year.year)"
      />
      <template v-if="expanded.has(year.year)">
        <template v-for="month in year.months" :key="month.month">
          <DateFilterTreeRow
              :label="monthLabel(month.month)"
              :checked="isTreeChecked(prefixes, month.month)"
              :indeterminate="isTreeIndeterminate(prefixes, month.month)"
              :depth="1"
              expandable
              :expanded="expanded.has(month.month)"
              :expand-label="t('tableFilter.expandMonth')"
              @toggle="toggle(month.month)"
              @expand="toggleExpand(month.month)"
          />
          <template v-if="expanded.has(month.month)">
            <DateFilterTreeRow
                v-for="day in month.days"
                :key="day"
                :label="formatDate(day)"
                :checked="isTreeChecked(prefixes, day)"
                :indeterminate="false"
                :depth="2"
                @toggle="toggle(day)"
            />
          </template>
        </template>
      </template>
    </template>
    <div v-if="tree.length === 0" class="py-2 text-center text-xs text-(--text-muted)">
      {{ t('tableFilter.noValues') }}
    </div>
  </div>
</template>
