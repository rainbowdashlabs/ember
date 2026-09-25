/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TodayEvents from './TodayEvents.vue'
import BreaksList from './BreaksList.vue'
import EventDashboardActions from './EventDashboardActions.vue'
import EventExportPanel from './EventExportPanel.vue'
import EventListSection from './EventListSection.vue'
import type {PagedListView} from '@/composables/usePagedList'
import type {AttendanceTemplate} from '@/api/attendance'
import type {DatedEvent, EventBreak, EventCategory, EventField, StationEvent} from '@/api/events'

/**
 * The dashboard under its tabs: what is coming, or what has been.
 *
 * <p>Today's appointments and the breaks belong to the current tab, because both are current by
 * definition. The export takes its own span of dates and so stands outside the tabs.
 */
const props = defineProps<{
  isPast: boolean
  todayEvents: StationEvent[]
  dates: PagedListView<DatedEvent>
  series: PagedListView<DatedEvent>
  isEmpty: boolean
  categories: EventCategory[]
  templates: AttendanceTemplate[]
  overviewFields: Record<number, EventField[]>
  breaks: EventBreak[]
}>()

defineEmits<{
  attendance: [event: StationEvent]
  addEvent: []
  editEvent: [event: StationEvent]
  deleteEvent: [event: StationEvent]
  addBreak: []
  editBreak: [entry: EventBreak]
  deleteBreak: [entry: EventBreak]
  importHolidays: []
  openExport: []
  loadMoreDates: []
  loadMoreSeries: []
}>()

const {t} = useI18n()

const dateTitle = computed(() => props.isPast ? t('events.pastDates') : t('events.upcomingDates'))
const seriesTitle = computed(() => props.isPast ? t('events.endedSeries') : t('events.series'))
const emptyMessage = computed(() => props.isPast ? t('events.noPastEvents') : t('events.noEvents'))
</script>

<template>
  <div class="space-y-6">
    <TodayEvents v-if="!isPast" :events="todayEvents" @attendance="event => $emit('attendance', event)"/>

    <NeutralContainer class="space-y-4">
      <EventDashboardActions @add-event="$emit('addEvent')"/>

      <EmptyState v-if="isEmpty" compact>{{ emptyMessage }}</EmptyState>

      <EventListSection
          :title="dateTitle"
          :list="dates"
          :is-past="isPast"
          :categories="categories"
          :templates="templates"
          :overview-fields="overviewFields"
          @edit="event => $emit('editEvent', event)"
          @remove="event => $emit('deleteEvent', event)"
          @load-more="$emit('loadMoreDates')"
      />

      <EventListSection
          :title="seriesTitle"
          :list="series"
          :is-past="isPast"
          :categories="categories"
          :templates="templates"
          :overview-fields="overviewFields"
          @edit="event => $emit('editEvent', event)"
          @remove="event => $emit('deleteEvent', event)"
          @load-more="$emit('loadMoreSeries')"
      />
    </NeutralContainer>

    <BreaksList
        v-if="!isPast"
        :breaks="breaks"
        @add="$emit('addBreak')"
        @delete="entry => $emit('deleteBreak', entry)"
        @edit="entry => $emit('editBreak', entry)"
        @import-holidays="$emit('importHolidays')"
    />

    <EventExportPanel @open="$emit('openExport')"/>
  </div>
</template>
