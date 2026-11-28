/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {RouterLink} from 'vue-router'
import type {CalendarBar, CalendarBlock} from '@/api/generated/schema'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {formatDate} from '@/util/format'
import {clashDays, columnsOf, placeBars, type RowRef} from './calendarGrid'

/**
 * The days of one calendar row: a cell per day that a drag starts or extends a block period on,
 * the block periods hatched across it, a bar per request line in lanes, and a mark on every day
 * that asks for more than the row holds.
 */
const props = defineProps<{
  row: RowRef
  days: string[]
  bars: CalendarBar[]
  blocks: CalendarBlock[]
  /** The days a drag on this row covers so far, highlighted. */
  dragging: {from: string; to: string} | null
  /** Whether a drag may start here; a kind has no block period of its own. */
  draggable: boolean
  /** The kinds of a product's row, whose shortages the row marks too. */
  kinds?: RowRef[]
}>()

const emit = defineEmits<{
  dragStart: [day: string]
  dragOver: [day: string]
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()
const placed = computed(() => placeBars(props.bars, props.days))
const lanes = computed(() => Math.max(1, ...placed.value.map(bar => bar.lane + 1)))
const clashes = computed(() => clashDays(props.bars, props.row, props.days, props.kinds ?? []))
const blockSpans = computed(() => props.blocks
    .map(block => ({block, columns: columnsOf(block.from, block.to, props.days)}))
    .filter(entry => entry.columns !== null))

const BAR_CLASS: Record<string, string> = {
  REQUESTED: 'border-2 border-primary bg-(--bg)',
  APPROVED: 'bg-primary/60',
  LENT: 'bg-primary text-primary-text',
  RETURNED: 'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)',
  DECLINED: 'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted) line-through',
  CLOSED: 'bg-bg-light-accent dark:bg-bg-dark-accent text-(--text-muted)',
}

function barLabel(bar: CalendarBar): string {
  return t('lendingCalendarPage.barLabel', {
    partner: bar.partner,
    quantity: bar.quantity,
    status: t(`lending.status.${bar.status}`),
    from: formatDate(bar.from),
    to: formatDate(bar.to),
  })
}

function link(bar: CalendarBar) {
  return routes.lendingRequest ? {name: routes.lendingRequest, params: {id: bar.requestId}} : undefined
}

function blockLabel(block: CalendarBlock): string {
  const what = block.pieceName ?? (block.inventoryId === null ? t('lendingCalendarPage.wholeStation') : t('lendingCalendarPage.thisProduct'))
  const days = {what, from: formatDate(block.from), to: formatDate(block.to)}
  return block.reason.trim()
      ? t('lendingCalendarPage.blockLabelReason', {...days, reason: block.reason})
      : t('lendingCalendarPage.blockLabel', days)
}

function start(event: PointerEvent, day: string) {
  if (event.button !== 0 || !props.draggable) return
  emit('dragStart', day)
}

function inDrag(day: string): boolean {
  return !!props.dragging && day >= props.dragging.from && day <= props.dragging.to
}
</script>

<template>
  <div class="relative grid gap-y-1 py-1" :style="{gridTemplateColumns: `repeat(${days.length}, minmax(2rem, 1fr))`, gridTemplateRows: `repeat(${lanes}, 1.5rem)`}">
    <div v-for="(day, index) in days" :key="day" aria-hidden="true" data-testid="lending-calendar-cell" :data-day="day"
         class="border-l border-(--border)" :class="[clashes.has(day) ? 'bg-error/15' : '', inDrag(day) ? 'bg-info/30' : '',
                                                      draggable ? 'cursor-crosshair' : '']"
         :style="{gridColumn: index + 1, gridRow: `1 / span ${lanes}`}"
         @pointerdown.prevent="start($event, day)" @pointerenter="emit('dragOver', day)"/>
    <div v-for="{block, columns} in blockSpans" :key="`block-${block.id}`" class="calendar-block pointer-events-none rounded-theme"
         :style="{gridColumn: `${columns!.start} / ${columns!.end + 1}`, gridRow: `1 / span ${lanes}`}" data-testid="lending-calendar-block"/>
    <component :is="link(item.bar) ? RouterLink : 'span'" v-for="item in placed" :key="`${item.bar.requestId}-${item.bar.artId}-${item.lane}`"
               :to="link(item.bar)" :title="barLabel(item.bar)" :aria-label="barLabel(item.bar)"
               class="z-10 truncate rounded-theme px-1 text-xs leading-6" data-testid="lending-calendar-bar"
               :class="item.bar.overdue ? 'bg-error text-white' : BAR_CLASS[item.bar.status]"
               :style="{gridColumn: `${item.start} / ${item.end + 1}`, gridRow: item.lane + 1}">
      {{ item.bar.partner }} · {{ item.bar.quantity }}
    </component>
    <span v-for="{block} in blockSpans" :key="`block-text-${block.id}`" class="sr-only">{{ blockLabel(block) }}</span>
    <span v-for="day in [...clashes]" :key="`clash-${day}`" class="sr-only">{{ t('lendingCalendarPage.clash', {day: formatDate(day)}) }}</span>
  </div>
</template>

<style scoped>
.calendar-block {
  background-image: repeating-linear-gradient(45deg, var(--border) 0 3px, transparent 3px 7px);
  opacity: 0.8;
}
</style>
