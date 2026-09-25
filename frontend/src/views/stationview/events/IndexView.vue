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
  loading, error, reload, loadMore,
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
  error,
})

const {
  show: showDeleteBreakModal,
  target: deleteBreakTarget,
  requestDelete: requestDeleteBreak,
  confirm: confirmDeleteBreak,
} = useConfirmDelete<EventBreak>({
  onDelete: entry => events.deleteBreak(entry.id),
  onSuccess: () => reload(),
  error,
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

async function saveBreak(data: { name: string; startDate: string; endDate: string }) {
  error.value = ''
  try {
    if (editingBreak.value) {
      await events.updateBreak(editingBreak.value.id, data)
    } else {
      await events.createBreak(data)
    }
    showBreakModal.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

async function onImportHolidays(holidays: Array<{ name: string; startDate: string; endDate: string }>) {
  error.value = ''
  try {
    for (const holiday of holidays) {
      await events.createBreak(holiday)
    }
    showHolidayModal.value = false
    await reload()
  } catch {
    error.value = t('common.error')
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
      <FailureAlert :message="error"/>

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
        @error="message => error = message"
        @save-break="saveBreak"
        @import-holidays="onImportHolidays"
        @confirm-delete-event="confirmDeleteEvent"
        @confirm-delete-break="confirmDeleteBreak"
    />
  </ViewContent>
</template>
