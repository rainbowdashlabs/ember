/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {AttendanceTemplate} from '@/api/attendance'
import type {StationEvent} from '@/api/events'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import TodayEventsGrid from '@/views/stationview/attendance/newview/TodayEventsGrid.vue'
import TemplateGrid from '@/views/stationview/attendance/newview/TemplateGrid.vue'
import NewSessionModal from '@/views/stationview/attendance/newview/NewSessionModal.vue'

/** How long a sheet runs where the template has no sheet of its own to go by yet. */
const DEFAULT_LENGTH_MS = 2 * 60 * 60 * 1000

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {loaded} = useSession()

const templates = ref<AttendanceTemplate[]>([])
const todayEvents = ref<StationEvent[]>([])

const chosenTemplate = ref<AttendanceTemplate | null>(null)
const suggestedStart = ref('')
const suggestedEnd = ref('')
const askTimes = ref(false)

const eventsWithTemplate = computed(() =>
    todayEvents.value.filter(ev => ev.templateId != null)
)

const {loading, error, reload} = useAsyncLoader(async () => {
  const [tpl, today] = await Promise.all([
    attendance.listTemplates(),
    events.listTodayEvents(),
  ])
  templates.value = tpl
  todayEvents.value = today

  const templateId = route.query.templateId ? Number(route.query.templateId) : null
  if (templateId) {
    const eventId = route.query.eventId ? Number(route.query.eventId) : null
    await createSession(templateId, eventId)
  }
}, {autoLoad: false})

const {running: creating, error: createError, run: runCreate} = useAsyncAction(
    async (templateId: number, eventId?: number | null, times?: {title: string; startTime: string; endTime: string}) => {
      const session = await attendance.createSession(templateId, {
        eventId: eventId ?? null,
        ...(times ?? {}),
      })
      askTimes.value = false
      router.push({name: 'attendance-session', params: {id: session.id}})
    })

const displayError = computed(() => error.value || createError.value)

function createSession(templateId: number, eventId?: number | null) {
  error.value = ''
  return runCreate(templateId, eventId)
}

/**
 * The times a new sheet is offered, taken from the last sheet this template ran.
 *
 * <p>A station that meets on Tuesday evenings meets for as long as it met last Tuesday, so that is
 * the better guess than any fixed length, and where a template has never been used a plain two
 * hours stands in.
 */
async function suggestSpan(templateId: number): Promise<number> {
  try {
    const previous = await attendance.listSessions(templateId)
    const last = previous.find(session => session.startTime && session.endTime)
    if (!last) return DEFAULT_LENGTH_MS
    const span = new Date(last.endTime!).getTime() - new Date(last.startTime!).getTime()
    return span > 0 ? span : DEFAULT_LENGTH_MS
  } catch {
    return DEFAULT_LENGTH_MS
  }
}

async function askForTimes(template: AttendanceTemplate) {
  error.value = ''
  chosenTemplate.value = template
  const start = new Date()
  start.setSeconds(0, 0)
  start.setMinutes(start.getMinutes() - (start.getMinutes() % 5))
  suggestedStart.value = start.toISOString()
  suggestedEnd.value = new Date(start.getTime() + await suggestSpan(template.id)).toISOString()
  askTimes.value = true
}

function createFromTemplate(templateId: number) {
  const template = templates.value.find(tpl => tpl.id === templateId)
  if (template) askForTimes(template)
}

function createWithTimes(times: {title: string; startTime: string; endTime: string}) {
  if (!chosenTemplate.value) return
  runCreate(chosenTemplate.value.id, null, times)
}

function createFromEvent(ev: StationEvent) {
  if (ev.templateId) {
    createSession(ev.templateId, ev.id)
  }
}

function getTemplateName(templateId: number): string {
  return templates.value.find(t => t.id === templateId)?.name ?? ''
}

watch(loaded, (isLoaded) => {
  if (isLoaded) reload()
}, {immediate: true})
</script>

<template>
  <ViewContent
      :title="t('pages.attendance-new.title')"
      :subtitle="t('pages.attendance-new.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="displayError" variant="error">{{ displayError }}</Alert>

      <template v-if="!loading && !creating">
        <TodayEventsGrid v-if="eventsWithTemplate.length > 0"
                         :events="eventsWithTemplate"
                         :template-name="getTemplateName"
                         @select="createFromEvent"/>
        <TemplateGrid :templates="templates" @select="createFromTemplate"/>
      </template>

      <div v-if="creating" class="flex items-center gap-2 justify-center py-8">
        <Spinner size="md"/>
        <span class="text-(--text-muted)">{{ t('attendanceNew.creating') }}</span>
      </div>

      <NewSessionModal
          v-model="askTimes"
          :busy="creating"
          :suggested-end="suggestedEnd"
          :suggested-start="suggestedStart"
          :template-name="chosenTemplate?.name ?? ''"
          @create="createWithTimes"
      />
    </div>
  </ViewContent>
</template>
