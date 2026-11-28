/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import * as lending from '@/api/lending'
import type {RemoteDay, RemoteDayCount} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDate, monthName, todayIsoDate, weekdayName} from '@/util/format'
import CalendarLegend from './CalendarLegend.vue'
import {inChosen, monthCells, monthOf, pickDay, rowKey, shiftMonth, type CalendarCell} from './calendarCells'

/**
 * What is free of a partner's product on each day of a month. A day is green where everything looked
 * at is free, amber where some is, grey where nothing is or the station lends nothing. Two clicks pick
 * the days, which then hold for the whole shop.
 */
const props = defineProps<{
  stationUid: string
  inventoryId: number
  /** One single piece to show alone, or undefined for the whole product. */
  itemId?: number
  /** What each row is called, keyed by {@link rowKey}; only rows named here can be picked alone. */
  rows: {key: string; label: string}[]
  /** The chosen days, the last one empty while only the first is set. */
  dateFrom: string
  dateTo: string
}>()

const emit = defineEmits<{
  pick: [from: string, to: string]
}>()

const {t} = useI18n()
const month = ref(monthOf(props.dateFrom || todayIsoDate()))
const choice = ref('all')
const days = ref<RemoteDay[]>([])

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  const answer = await lending.getProductDays(props.stationUid, props.inventoryId, month.value, props.itemId)
  if (isCurrent()) days.value = answer.days
})
watch([month, () => props.stationUid, () => props.inventoryId, () => props.itemId], () => {
  days.value = []
  void reload()
})

const labels = computed(() => new Map(props.rows.map(row => [row.key, row.label])))
const cells = computed(() => monthCells(days.value, choice.value))
const weekdays = [1, 2, 3, 4, 5, 6, 7].map(number => weekdayName(number).slice(0, 2))
const heading = computed(() => {
  const [year = 1970, number = 1] = month.value.split('-').map(Number)
  return `${monthName(number)} ${year}`
})

function describe(cell: Extract<CalendarCell, {kind: 'day'}>): string {
  const text = describeCounts(cell)
  return chosen(cell.day) ? `${text} (${t('lendingCalendar.chosen')})` : text
}

function describeCounts(cell: Extract<CalendarCell, {kind: 'day'}>): string {
  if (cell.state === 'blocked') return t('lendingCalendar.dayBlocked', {day: formatDate(cell.day)})
  const lines = cell.counts.map((count: RemoteDayCount) => t('lendingCalendar.countLine', {
    name: labels.value.get(rowKey(count)) ?? '',
    free: count.free,
    offered: count.offered,
  }))
  return `${formatDate(cell.day)}: ${lines.join(', ')}`
}

const STATE_CLASS: Record<string, string> = {
  all: 'bg-success/25',
  some: 'bg-info/30',
  none: 'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)',
  blocked: 'calendar-blocked text-(--text-muted)',
}

function chosen(day: string): boolean {
  return inChosen(day, props.dateFrom, props.dateTo)
}

function pick(day: string) {
  const next = pickDay(day, props.dateFrom, props.dateTo)
  emit('pick', next.from, next.to)
}
</script>

<template>
  <section class="space-y-3 rounded-theme border border-(--border) p-4" data-testid="lending-calendar">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>{{ t('lendingCalendar.title') }} · {{ heading }}</SubHeader>
      <div class="flex items-center gap-1">
        <IconButton :icon="['fas', 'chevron-left']" :label="t('lendingCalendar.previous')" @click="month = shiftMonth(month, -1)"/>
        <IconButton :icon="['fas', 'chevron-right']" :label="t('lendingCalendar.next')" @click="month = shiftMonth(month, 1)"/>
      </div>
    </div>
    <div v-if="rows.length > 1" class="flex flex-wrap gap-1" role="group" :aria-label="t('lendingCalendar.title')">
      <SelectionToggleButton v-for="row in [{key: 'all', label: t('lendingCalendar.all')}, ...rows]" :key="row.key"
                             :selected="choice === row.key" @toggle="choice = row.key">
        {{ row.label }}
      </SelectionToggleButton>
    </div>
    <FailureAlert :failure="failure"/>
    <div class="grid grid-cols-7 gap-1 text-center" :aria-busy="loading">
      <span v-for="weekday in weekdays" :key="weekday" class="text-xs font-medium text-(--text-muted)">{{ weekday }}</span>
      <template v-for="cell in cells" :key="cell.key">
        <span v-if="cell.kind === 'blank'"/>
        <BareButton v-else class="min-h-10 rounded-theme text-sm" :class="[STATE_CLASS[cell.state],
                    chosen(cell.day) ? 'ring-2 ring-primary' : '']" :aria-pressed="chosen(cell.day)"
                    :title="describe(cell)" :aria-label="describe(cell)" :data-day="cell.day" @click="pick(cell.day)">
          {{ cell.number }}
        </BareButton>
      </template>
    </div>
    <CalendarLegend/>
    <MutedText tag="p">{{ t('lendingCalendar.pickHint') }}</MutedText>
  </section>
</template>

<style scoped>
.calendar-blocked {
  background-image: repeating-linear-gradient(45deg, var(--border) 0 3px, transparent 3px 7px);
}
</style>
