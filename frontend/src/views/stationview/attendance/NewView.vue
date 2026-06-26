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
import type {AttendanceTemplate, StationEvent} from '@/api/types'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import TodayEventsGrid from '@/views/stationview/attendance/newview/TodayEventsGrid.vue'
import TemplateGrid from '@/views/stationview/attendance/newview/TemplateGrid.vue'
import {formatTime} from '@/util/format'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {loaded} = useSession()

const templates = ref<AttendanceTemplate[]>([])
const todayEvents = ref<StationEvent[]>([])
const creating = ref(false)

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

async function createSession(templateId: number, eventId?: number | null) {
  creating.value = true
  error.value = ''
  try {
    const session = await attendance.createSession(templateId, {
      eventId: eventId ?? null,
    })
    router.push({name: 'attendance-session', params: {id: session.id}})
  } catch {
    error.value = t('common.error')
  } finally {
    creating.value = false
  }
}

function createFromTemplate(templateId: number) {
  createSession(templateId)
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
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && !creating">
        <TodayEventsGrid v-if="eventsWithTemplate.length > 0"
                         :events="eventsWithTemplate"
                         :template-name="getTemplateName"
                         :format-time="formatTime"
                         @select="createFromEvent"/>
        <TemplateGrid :templates="templates" @select="createFromTemplate"/>
      </template>

      <div v-if="creating" class="flex items-center gap-2 justify-center py-8">
        <Spinner size="md"/>
        <span class="text-(--text-muted)">{{ t('attendanceNew.creating') }}</span>
      </div>
    </div>
  </ViewContent>
</template>
