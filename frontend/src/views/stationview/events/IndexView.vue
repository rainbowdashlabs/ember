/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EventDashboardBody from './indexview/EventDashboardBody.vue'
import EventDashboardModals from './indexview/EventDashboardModals.vue'
import type {AttendanceTemplate, EventBreak, EventCategory, EventField, StationEvent} from '@/api/types'
import {attendance, events} from '@/api'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

const {t} = useI18n()
const router = useRouter()
const allEvents = ref<StationEvent[]>([])
const todayEvents = ref<StationEvent[]>([])
const breaks = ref<EventBreak[]>([])
const categories = ref<EventCategory[]>([])
const templates = ref<AttendanceTemplate[]>([])
const overviewFields = ref<Record<number, EventField[]>>({})

interface CategoryGroup {
  category: EventCategory | null
  events: StationEvent[]
}

const eventsByCategory = computed((): CategoryGroup[] => {
  const groups: CategoryGroup[] = []
  const sorted = [...categories.value].sort((a, b) => a.position - b.position)

  const sortByStart = (a: StationEvent, b: StationEvent) =>
      (a.startTime ?? '').localeCompare(b.startTime ?? '')

  for (const cat of sorted) {
    const catEvents = allEvents.value.filter(e => e.categoryId === cat.id).sort(sortByStart)
    if (catEvents.length > 0) {
      groups.push({category: cat, events: catEvents})
    }
  }

  const uncategorized = allEvents.value.filter(e => !e.categoryId).sort(sortByStart)
  if (uncategorized.length > 0) {
    groups.push({category: null, events: uncategorized})
  }

  return groups
})

const showBreakModal = ref(false)
const editingBreak = ref<EventBreak | null>(null)
const showHolidayModal = ref(false)
const showExportModal = ref(false)

const {loading, error, reload} = useAsyncLoader(async () => {
  const [ev, today, br, cats, tpl, ovFields] = await Promise.all([
    events.listEvents(),
    events.listTodayEvents(),
    events.listBreaks(),
    events.listCategories(),
    attendance.listTemplates(),
    events.getOverviewFields(),
  ])
  allEvents.value = ev
  todayEvents.value = today
  breaks.value = br
  categories.value = cats
  templates.value = tpl
  overviewFields.value = ovFields
})

const {
  show: showDeleteEventModal,
  target: deleteEventTarget,
  requestDelete: requestDeleteEvent,
  confirm: confirmDeleteEvent,
} = useConfirmDelete<StationEvent>({
  onDelete: ev => events.deleteEvent(ev.id),
  onSuccess: () => reload(),
  error,
})

const {
  show: showDeleteBreakModal,
  target: deleteBreakTarget,
  requestDelete: requestDeleteBreak,
  confirm: confirmDeleteBreak,
} = useConfirmDelete<EventBreak>({
  onDelete: br => events.deleteBreak(br.id),
  onSuccess: () => reload(),
  error,
})

function openAddEvent() {
  router.push({name: 'event-new'})
}

function openEditEvent(ev: StationEvent) {
  router.push({name: 'event-edit', params: {id: ev.id}})
}

function openAddBreak() {
  editingBreak.value = null
  showBreakModal.value = true
}

function openEditBreak(br: EventBreak) {
  editingBreak.value = br
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
    for (const h of holidays) {
      await events.createBreak(h)
    }
    showHolidayModal.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

function goToAttendance(ev: StationEvent) {
  if (ev.templateId) {
    router.push({name: 'attendance-new', query: {templateId: String(ev.templateId), eventId: String(ev.id)}})
  }
}

</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <EventDashboardBody
          v-if="!loading"
          :today-events="todayEvents"
          :events-by-category="eventsByCategory"
          :has-events="allEvents.length > 0"
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
      />

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
          @error="e => error = e"
          @save-break="saveBreak"
          @import-holidays="onImportHolidays"
          @confirm-delete-event="confirmDeleteEvent"
          @confirm-delete-break="confirmDeleteBreak"
      />
    </div>
  </ViewContent>
</template>
