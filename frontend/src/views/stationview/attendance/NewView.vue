/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {AttendanceTemplate, StationEvent} from '@/api/types'
import {attendance, events} from '@/api'
import {useSession} from '@/composables/useSession'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const router = useRouter()
const route = useRoute()
const {loaded} = useSession()

const templates = ref<AttendanceTemplate[]>([])
const todayEvents = ref<StationEvent[]>([])
const loading = ref(true)
const creating = ref(false)
const error = ref('')

const eventsWithTemplate = computed(() =>
    todayEvents.value.filter(ev => ev.templateId != null)
)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [tpl, today] = await Promise.all([
      attendance.listTemplates(),
      events.listTodayEvents(),
    ])
    templates.value = tpl
    todayEvents.value = today

    // If opened with query params, create session immediately
    const templateId = route.query.templateId ? Number(route.query.templateId) : null
    if (templateId) {
      const eventId = route.query.eventId ? Number(route.query.eventId) : null
      await createSession(templateId, eventId)
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && !creating">
        <!-- Today's events with attendance templates -->
        <div v-if="eventsWithTemplate.length > 0" class="space-y-3">
          <SectionHeader>{{ t('attendanceNew.todayEvents') }}</SectionHeader>
          <div class="grid gap-3 sm:grid-cols-2">
            <PrimaryContainer
                v-for="ev in eventsWithTemplate"
                :key="ev.id"
                class="space-y-2 cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
                @click="createFromEvent(ev)"
            >
              <div class="flex items-center justify-between">
                <span class="font-semibold">{{ ev.name }}</span>
                <span class="text-sm">{{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}</span>
              </div>
              <p v-if="ev.description" class="text-sm text-(--text-muted)">{{ ev.description }}</p>
              <p class="text-xs text-(--text-muted)">
                {{ t('attendanceNew.template') }}: {{ getTemplateName(ev.templateId!) }}
              </p>
            </PrimaryContainer>
          </div>
        </div>

        <!-- All templates -->
        <div class="space-y-3">
          <SectionHeader>{{ t('attendanceNew.fromTemplate') }}</SectionHeader>
          <MutedText tag="div" size="sm" class="py-2" v-if="templates.length === 0">
            {{ t('attendanceNew.noTemplates') }}
          </MutedText>
          <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
            <NeutralContainer
                v-for="tpl in templates"
                :key="tpl.id"
                class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
                @click="createFromTemplate(tpl.id)"
            >
              <div class="flex items-center justify-between">
                <span class="font-medium">{{ tpl.name }}</span>
                <PrimaryButton :icon="['fas', 'plus']">
                  {{ t('attendanceNew.create') }}
                </PrimaryButton>
              </div>
            </NeutralContainer>
          </div>
        </div>
      </template>

      <div v-if="creating" class="flex items-center gap-2 justify-center py-8">
        <Spinner size="md"/>
        <span class="text-(--text-muted)">{{ t('attendanceNew.creating') }}</span>
      </div>
    </div>
  </ViewContent>
</template>
