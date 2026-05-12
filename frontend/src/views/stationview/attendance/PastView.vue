/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {attendance} from '@/api'
import type {SessionSummary} from '@/api/attendance'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

const sessions = ref<SessionSummary[]>([])
const loading = ref(true)
const error = ref('')

function formatDate(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return d.toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    sessions.value = await attendance.listSessionSummaries()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function openSession(id: number) {
  router.push({name: 'attendance-session', params: {id}})
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

      <template v-if="!loading">
        <div v-if="sessions.length === 0" class="text-(--text-muted) text-sm text-center py-4">
          {{ t('attendancePast.empty') }}
        </div>

        <div class="space-y-2">
          <NeutralContainer
              v-for="s in sessions"
              :key="s.id"
              class="cursor-pointer hover:ring-2 hover:ring-primary/30 transition-all"
              @click="openSession(s.id)"
          >
            <div class="flex items-center justify-between flex-wrap gap-3">
              <div>
                <span class="font-semibold text-sm">{{ s.title || t('attendancePast.untitled') }}</span>
                <span class="ml-3 text-sm text-(--text-muted)">{{ formatDate(s.createdAt) }}</span>
                <span class="ml-2 text-xs text-(--text-muted)">{{ formatTime(s.startTime) }} – {{
                    formatTime(s.endTime)
                  }}</span>
              </div>
              <div class="flex items-center gap-2 text-xs">
                <SuccessBadge>{{ s.presentCount }} {{ t('attendancePast.present') }}</SuccessBadge>
                <ErrorBadge>{{ s.absentCount }} {{ t('attendancePast.absent') }}</ErrorBadge>
                <InfoBadge>{{ s.declinedCount }} {{ t('attendancePast.declined') }}</InfoBadge>
                <SecondaryBadge v-if="s.unconfirmedCount > 0">{{ s.unconfirmedCount }}
                  {{ t('attendancePast.unconfirmed') }}
                </SecondaryBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
