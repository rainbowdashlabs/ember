/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import {useEventRoutes} from '@/composables/useEventRoutes'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import EventDashboardBody from './indexview/EventDashboardBody.vue'
import EventDashboardModals from './indexview/EventDashboardModals.vue'
import EventFilterBar from './eventshared/EventFilterBar.vue'
import {useEventDashboard} from './indexview/useEventDashboard'
import {EventKinds, EventStates, type EventBreak, type StationEvent} from '@/api/events'
import {events} from '@/api'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {describeFailure, saying} from '@/util/failure'

defineProps<{
  /** The heading, when the station's own wording is not the right one. */
  title?: string
  subtitle?: string
}>()

const {t} = useI18n()
const router = useRouter()
const eventRoutes = useEventRoutes()

const {
  tab, isPast, searchInput, categoryId, from, to,
  todayEvents, breaks, categories, templates, overviewFields,
  dates, series, isEmpty,
  loading, failure, reload, loadMore,
} = useEventDashboard()

const tabs = computed(() => [
  {key: EventStates.CURRENT, label: t('events.tabCurrent')},
  {key: EventStates.PAST, label: t('events.tabPast')},
])

const showBreakModal = ref(false)
const editingBreak = ref<EventBreak | null>(null)
const showHolidayModal = ref(false)
const showExportModal = ref(false)

const {
  show: showDeleteEventModal,
  target: deleteEventTarget,
  requestDelete: requestDeleteEvent,
  confirm: confirmDeleteEvent,
} = useConfirmDelete<StationEvent>({
  onDelete: event => events.deleteEvent(event.id),
  onSuccess: () => reload(),
  failure,
})

const {
  show: showDeleteBreakModal,
  target: deleteBreakTarget,
  requestDelete: requestDeleteBreak,
  confirm: confirmDeleteBreak,
} = useConfirmDelete<EventBreak>({
  onDelete: entry => events.deleteBreak(entry.id),
  onSuccess: () => reload(),
  failure,
})

function openAddEvent() {
  router.push({name: eventRoutes.create})
}

function openEditEvent(event: StationEvent) {
  router.push({name: eventRoutes.edit, params: {id: event.id}})
}

function openAddBreak() {
  editingBreak.value = null
  showBreakModal.value = true
}

function openEditBreak(entry: EventBreak) {
  editingBreak.value = entry
  showBreakModal.value = true
}

/**
 * Writes the break, then catches the page up, and answers for the two separately.
 *
 * <p>They shared an attempt, so a break that was written and a page that then failed to come back
 * both read as a refused break, and the reader writes the same closure twice.
 */
async function saveBreak(data: { name: string; startDate: string; endDate: string }) {
  failure.value = null
  try {
    if (editingBreak.value) {
      await events.updateBreak(editingBreak.value.id, data)
    } else {
      await events.createBreak(data)
    }
    showBreakModal.value = false
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await catchUp()
}

/**
 * Writes each school holiday as a break of its own.
 *
 * <p>How many went in is said where some did, because the list comes from a directory of dozens and
 * a reader told only that it failed cannot tell whether to run it again or fix the rest by hand.
 */
async function onImportHolidays(holidays: Array<{ name: string; startDate: string; endDate: string }>) {
  failure.value = null
  let written = 0
  try {
    for (const holiday of holidays) {
      await events.createBreak(holiday)
      written++
    }
    showHolidayModal.value = false
  } catch (e) {
    const described = describeFailure(e, t)
    failure.value = written === 0
        ? described
        : saying(described, t('events.holidaysImportedPartly', {written, total: holidays.length}))
    await catchUp()
    return
  }
  await catchUp()
}

/** Fetches the page again, saying so where that is the only thing that failed. */
async function catchUp() {
  try {
    await reload()
  } catch (e) {
    failure.value = saying(describeFailure(e, t), t('failure.staleAfterAction'))
  }
}

function goToAttendance(event: StationEvent) {
  if (event.templateId) {
    router.push({name: 'attendance-new', query: {templateId: String(event.templateId), eventId: String(event.id)}})
  }
}

</script>

<template>
  <ViewContent
      :title="title ?? t('pages.events.title')"
      :subtitle="subtitle ?? t('pages.events.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure"/>

      <TabBar v-model="tab" :tabs="tabs"/>

      <EventFilterBar
          v-model:search="searchInput"
          v-model:category-id="categoryId"
          v-model:from="from"
          v-model:to="to"
          :categories="categories"
      />

      <EventDashboardBody
          v-if="!loading"
          :is-past="isPast"
          :today-events="todayEvents"
          :dates="dates"
          :series="series"
          :is-empty="isEmpty"
          :categories="categories"
          :templates="templates"
          :overview-fields="overviewFields"
          :breaks="breaks"
          @attendance="goToAttendance"
          @add-event="openAddEvent"
          @edit-event="openEditEvent"
          @delete-event="requestDeleteEvent"
          @add-break="openAddBreak"
          @edit-break="openEditBreak"
          @delete-break="requestDeleteBreak"
          @import-holidays="showHolidayModal = true"
          @open-export="showExportModal = true"
          @load-more-dates="loadMore(EventKinds.ONE_TIME)"
          @load-more-series="loadMore(EventKinds.REPEATING)"
      />
    </div>

    <EventDashboardModals
        v-model:show-export="showExportModal"
        v-model:show-break="showBreakModal"
        v-model:show-holiday="showHolidayModal"
        v-model:show-delete-event="showDeleteEventModal"
        v-model:show-delete-break="showDeleteBreakModal"
        :categories="categories"
        :editing-break="editingBreak"
        :delete-event-target="deleteEventTarget"
        :delete-break-target="deleteBreakTarget"
        @error="refused => failure = refused"
        @save-break="saveBreak"
        @import-holidays="onImportHolidays"
        @confirm-delete-event="confirmDeleteEvent"
        @confirm-delete-break="confirmDeleteBreak"
    />
  </ViewContent>
</template>
