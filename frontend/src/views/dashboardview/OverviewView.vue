/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type { NotificationEntry } from '@/api/types'
import { notifications } from '@/api'

const { t } = useI18n()

const items = ref<NotificationEntry[]>([])
const loading = ref(true)

const typeLabels: Record<string, string> = {
  NEW_NEWS: 'Neuigkeit',
  EVENT_REGISTRATION_STATUS: 'Anmeldung',
  EXCHANGE_STATUS_CHANGE: 'Tausch',
  EXCHANGE_NEW_REQUEST: 'Neue Tausch-Anfrage',
  NEW_EVENT: 'Neuer Termin',
  MEMBER_ADDED_TO_GROUP: 'Gruppenänderung',
  PROFILE_FIELD_CHANGED: 'Profiländerung',
}

const typeIcons: Record<string, string> = {
  NEW_NEWS: 'newspaper',
  EVENT_REGISTRATION_STATUS: 'calendar-days',
  EXCHANGE_STATUS_CHANGE: 'rotate',
  EXCHANGE_NEW_REQUEST: 'rotate',
  NEW_EVENT: 'calendar-plus',
  MEMBER_ADDED_TO_GROUP: 'layer-group',
  PROFILE_FIELD_CHANGED: 'user',
}

async function loadData() {
  loading.value = true
  try {
    items.value = await notifications.listUnacknowledged()
  } catch { /* ignore */ }
  loading.value = false
}

async function ack(id: number) {
  await notifications.acknowledge(id)
  items.value = items.value.filter(n => n.id !== id)
}

async function ackAll() {
  await notifications.acknowledgeAll()
  items.value = []
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />

      <template v-if="!loading">
        <div v-if="items.length > 0" class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>{{ t('dashboard.notifications') }} ({{ items.length }})</SectionHeader>
            <SecondaryButton class="text-sm" @click="ackAll">
              <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1" />
              {{ t('dashboard.acknowledgeAll') }}
            </SecondaryButton>
          </div>

          <div class="space-y-2">
            <NeutralContainer v-for="n in items" :key="n.id" class="flex items-start justify-between gap-3 py-2 px-3">
              <div class="flex items-start gap-3">
                <font-awesome-icon :icon="['fas', typeIcons[n.type] ?? 'bell']" class="text-primary mt-0.5 h-4 w-4 shrink-0" />
                <div>
                  <span class="text-xs font-semibold text-(--text-muted)">{{ typeLabels[n.type] ?? n.type }}</span>
                  <p class="text-sm">{{ n.message }}</p>
                  <p class="text-xs text-(--text-muted)">{{ formatDate(n.createdAt) }}</p>
                </div>
              </div>
              <button type="button" class="text-xs text-primary hover:underline shrink-0 mt-1" @click="ack(n.id)">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-0.5" />
                {{ t('dashboard.acknowledge') }}
              </button>
            </NeutralContainer>
          </div>
        </div>

        <div v-else class="text-center text-(--text-muted) py-12">
          <font-awesome-icon :icon="['fas', 'check-double']" class="text-4xl text-success mb-3" />
          <p>{{ t('dashboard.noNotifications') }}</p>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
