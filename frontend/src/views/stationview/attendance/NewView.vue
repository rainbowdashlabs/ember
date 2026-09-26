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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {SessionAudience, TemplateDetail} from '@/api/attendance'
import type {StationEvent} from '@/api/events'
import type {MemberGroup} from '@/api/types'
import {attendance, events, memberGroups} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import TodayEventsGrid from '@/views/stationview/attendance/newview/TodayEventsGrid.vue'
import TemplateGrid from '@/views/stationview/attendance/newview/TemplateGrid.vue'
import EmptySessionTile from '@/views/stationview/attendance/newview/EmptySessionTile.vue'
import AudienceStep from '@/views/stationview/attendance/newview/AudienceStep.vue'
import NewSessionModal from '@/views/stationview/attendance/newview/NewSessionModal.vue'

/** How long a sheet runs where the template has no sheet of its own to go by yet. */
const DEFAULT_LENGTH_MS = 2 * 60 * 60 * 1000

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {loaded} = useSession()

const templates = ref<TemplateDetail[]>([])
const todayEvents = ref<StationEvent[]>([])
const groups = ref<MemberGroup[]>([])

const chosenTemplate = ref<TemplateDetail | null>(null)
const suggestedStart = ref('')
const suggestedEnd = ref('')
const askTimes = ref(false)

/** The audience for a sheet no template describes, set only by the second step and held until the times are. */
const chosenAudience = ref<SessionAudience | null>(null)
const askingAudience = ref(false)

const eventsWithTemplate = computed(() =>
    todayEvents.value.filter(ev => ev.templateId != null)
)

const {loading, failure: loadFailure, reload} = useAsyncLoader(async () => {
  const [tpl, today, known] = await Promise.all([
    attendance.listTemplateDetails(),
    events.listTodayEvents(),
    memberGroups.listGroups(),
  ])
  templates.value = tpl
  todayEvents.value = today
  groups.value = known

  const templateId = route.query.templateId ? Number(route.query.templateId) : null
  if (templateId) {
    const eventId = route.query.eventId ? Number(route.query.eventId) : null
    const date = typeof route.query.date === 'string' ? route.query.date : null
    await createSession(templateId, eventId, date)
  }
}, {autoLoad: false})

const {running: creating, failure: createFailure, run: runCreate} = useAsyncAction(
    async (
        templateId: number,
        eventId?: number | null,
        eventDate?: string | null,
        times?: {title: string; startTime: string; endTime: string},
    ) => {
      const session = await attendance.createSession(templateId, {
        eventId: eventId ?? null,
        ...(eventDate ? {eventDate} : {}),
        ...(times ?? {}),
        ...(chosenAudience.value ? {audience: chosenAudience.value} : {}),
      })
      askTimes.value = false
      router.push({name: 'attendance-session', params: {id: session.id}})
    })

const displayFailure = computed(() => createFailure.value ?? loadFailure.value)

function createSession(templateId: number, eventId?: number | null, eventDate?: string | null) {
  loadFailure.value = null
  return runCreate(templateId, eventId, eventDate)
}

/**
 * The times a new sheet is offered, taken from the last sheet this template ran.
 *
 * <p>A station that meets on Tuesdays meets for as long as it met last Tuesday, so that is
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

async function askForTimes(template: TemplateDetail) {
  loadFailure.value = null
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
  chosenAudience.value = null
  if (template) askForTimes(template)
}

function startEmpty() {
  loadFailure.value = null
  chosenAudience.value = null
  askingAudience.value = true
}

/** The audience is answered, so the sheet is now started the way any other one is: by its times. */
function audienceChosen(templateId: number, audience: SessionAudience) {
  const template = templates.value.find(tpl => tpl.id === templateId)
  if (!template) return
  chosenAudience.value = audience
  askingAudience.value = false
  askForTimes(template)
}

function createWithTimes(times: {title: string; startTime: string; endTime: string}) {
  if (!chosenTemplate.value) return
  runCreate(chosenTemplate.value.id, null, null, times)
}

function createFromEvent(ev: StationEvent) {
  if (ev.templateId) {
    chosenAudience.value = null
    createSession(ev.templateId, ev.id)
  }
}

function getTemplateName(templateId: number): string {
  return templates.value.find(t => t.id === templateId)?.name ?? ''
}

function groupName(groupId: number): string {
  return groups.value.find(group => group.id === groupId)?.name ?? ''
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
      <FailureAlert :failure="displayFailure"/>

      <AudienceStep
          v-if="askingAudience && !creating"
          :busy="creating"
          :groups="groups"
          :templates="templates"
          @back="askingAudience = false"
          @confirm="audienceChosen"
      />

      <template v-else-if="!loading && !creating">
        <TodayEventsGrid v-if="eventsWithTemplate.length > 0"
                         :events="eventsWithTemplate"
                         :template-name="getTemplateName"
                         @select="createFromEvent"/>
        <TemplateGrid :group-name="groupName" :templates="templates" @select="createFromTemplate"/>
        <EmptySessionTile v-if="templates.length > 0" @start="startEmpty"/>
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
