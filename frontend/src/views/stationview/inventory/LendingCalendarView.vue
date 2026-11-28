/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import IconButton from '@/components/button/IconButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import * as lending from '@/api/lending'
import type {LendingCalendar} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useRouteQueryRef} from '@/composables/useRouteQueryRef'
import {useSession} from '@/composables/useSession'
import {formatDate, shiftIsoDate, toIsoDate, todayIsoDate} from '@/util/format'
import CalendarHeader from './lendingcalendarview/CalendarHeader.vue'
import CalendarTrack from './lendingcalendarview/CalendarTrack.vue'
import CreateBlockModal from './lendingcalendarview/CreateBlockModal.vue'
import CalendarBarLegend from './lendingcalendarview/CalendarBarLegend.vue'
import {barsOf, blocksOf, CALENDAR_DAYS, daysFrom, dragSpan} from './lendingcalendarview/calendarGrid'

/**
 * The lending calendar: every product of the station as a row, four weeks wide, with the requests
 * for it as bars and its block periods hatched. A drag over a product's days sets them aside for it,
 * a drag over the dates for the whole station; the list of block periods stays one link away.
 */
const {t} = useI18n()
const {loaded} = useSession()
const routes = useInventoryRoutes()
const today = todayIsoDate()
const startRaw = useRouteQueryRef('from')
const start = computed(() => isDay(startRaw.value) ? startRaw.value : shiftIsoDate(today, -3))

/** Whether a text is a calendar day as yyyy-MM-dd, one that exists. */
function isDay(value: string): boolean {
  return /^\d{4}-\d{2}-\d{2}$/.test(value) && toIsoDate(new Date(`${value}T00:00:00`)) === value
}
const days = computed(() => daysFrom(start.value))
const calendar = ref<LendingCalendar | null>(null)
const open = ref(new Set<number>())

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  if (!loaded.value) return
  const answer = await lending.getCalendar(days.value[0]!, days.value.at(-1)!)
  if (isCurrent()) calendar.value = answer
})
watch([loaded, start], () => { void reload() })

function move(by: number) {
  startRaw.value = shiftIsoDate(start.value, by)
}

function toggle(inventoryId: number) {
  const next = new Set(open.value)
  if (next.has(inventoryId)) next.delete(inventoryId)
  else next.add(inventoryId)
  open.value = next
}

/** A drag in progress: where it started, where it is now, and which product it is on, null for the dates. */
interface Drag {
  inventoryId: number | null
  from: string
  to: string
}

/** The days a finished drag set aside, waiting for a reason. */
interface PendingBlock extends Drag {
  label: string
}

const drag = ref<Drag | null>(null)
const pending = ref<PendingBlock | null>(null)
const blockOpen = computed({
  get: () => pending.value !== null,
  set: (value: boolean) => { if (!value) pending.value = null },
})

function dragSpanOf(inventoryId: number | null) {
  return drag.value && drag.value.inventoryId === inventoryId ? dragSpan(drag.value.from, drag.value.to) : null
}

function finishDrag() {
  if (!drag.value) return
  const span = dragSpan(drag.value.from, drag.value.to)
  const product = calendar.value?.rows.find(row => row.inventoryId === drag.value?.inventoryId)
  pending.value = {...span, inventoryId: drag.value.inventoryId, label: product?.name ?? t('lendingCalendarPage.wholeStation')}
  drag.value = null
}

onMounted(() => window.addEventListener('pointerup', finishDrag))
onBeforeUnmount(() => window.removeEventListener('pointerup', finishDrag))
</script>

<template>
  <ViewContent :title="t('pages.inventory-lending-calendar.title')" :subtitle="t('pages.inventory-lending-calendar.subtitle')">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <div class="flex items-center gap-1">
        <IconButton :icon="['fas', 'chevron-left']" :label="t('lendingCalendarPage.earlier')" @click="move(-CALENDAR_DAYS)"/>
        <SecondaryButton @click="startRaw = ''">{{ t('lendingCalendarPage.today') }}</SecondaryButton>
        <IconButton :icon="['fas', 'chevron-right']" :label="t('lendingCalendarPage.later')" @click="move(CALENDAR_DAYS)"/>
        <MutedText class="ml-2">{{ formatDate(days[0]) }} - {{ formatDate(days.at(-1)) }}</MutedText>
      </div>
      <ButtonRow align="end">
        <SecondaryButton :icon="['fas', 'list']" :to="{name: routes.lendingBlocks}">{{ t('lendingCalendarPage.blockList') }}</SecondaryButton>
        <SecondaryButton :icon="['fas', 'plus']" :to="{name: routes.lendingBlocksCreate}">{{ t('lendingCalendarPage.addBlock') }}</SecondaryButton>
      </ButtonRow>
    </div>
    <MutedText tag="p" class="mb-2">{{ t('lendingCalendarPage.dragHint') }}</MutedText>
    <CalendarBarLegend class="mb-3"/>
    <AsyncSection :failure="failure" :loading="loading && !calendar" :empty="calendar?.rows.length === 0" :empty-message="t('lendingCalendarPage.empty')">
      <div v-if="calendar" class="overflow-x-auto" data-testid="lending-calendar">
        <div class="grid min-w-[64rem] grid-cols-[12rem_1fr]">
          <span/>
          <CalendarHeader :days="days" :today="today" :dragging="dragSpanOf(null)"
                          @drag-start="day => drag = {inventoryId: null, from: day, to: day}"
                          @drag-over="day => { if (drag && drag.inventoryId === null) drag.to = day }"/>
          <template v-for="row in calendar.rows" :key="row.inventoryId">
            <div class="flex items-center gap-1 border-t border-(--border) pr-2 text-sm">
              <LinkButton v-if="row.kinds.length > 0" :aria-expanded="open.has(row.inventoryId)"
                          :aria-label="t('lendingCalendarPage.showKinds', {name: row.name})" @click="toggle(row.inventoryId)">
                {{ open.has(row.inventoryId) ? '−' : '+' }}
              </LinkButton>
              <span class="truncate font-medium" :title="row.name">{{ row.name }}</span>
            </div>
            <CalendarTrack class="border-t border-(--border)" :row="{inventoryId: row.inventoryId, artId: null, pieceCount: row.pieceCount}"
                           :days="days" :bars="barsOf(calendar.bars, {inventoryId: row.inventoryId, artId: null, pieceCount: row.pieceCount})"
                           :blocks="blocksOf(calendar.blocks, {inventoryId: row.inventoryId, artId: null})" :dragging="dragSpanOf(row.inventoryId)" draggable
                           :kinds="row.kinds.map(kind => ({inventoryId: row.inventoryId, artId: kind.artId, pieceCount: kind.pieceCount}))"
                           @drag-start="day => drag = {inventoryId: row.inventoryId, from: day, to: day}"
                           @drag-over="day => { if (drag && drag.inventoryId === row.inventoryId) drag.to = day }"/>
            <template v-if="open.has(row.inventoryId)">
              <template v-for="kind in row.kinds" :key="kind.artId">
                <span class="truncate pl-6 text-sm" :title="kind.name">{{ kind.name }}</span>
                <CalendarTrack :row="{inventoryId: row.inventoryId, artId: kind.artId, pieceCount: kind.pieceCount}" :days="days"
                               :bars="barsOf(calendar.bars, {inventoryId: row.inventoryId, artId: kind.artId, pieceCount: kind.pieceCount})"
                               :blocks="blocksOf(calendar.blocks, {inventoryId: row.inventoryId, artId: kind.artId})" :dragging="null" :draggable="false"/>
              </template>
            </template>
          </template>
        </div>
      </div>
    </AsyncSection>
    <CreateBlockModal v-if="pending" v-model="blockOpen" :from="pending.from" :to="pending.to" :inventory-id="pending.inventoryId"
                      :label="pending.label" @created="reload()"/>
  </ViewContent>
</template>
