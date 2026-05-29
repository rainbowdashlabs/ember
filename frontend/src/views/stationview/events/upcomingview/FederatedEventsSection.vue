/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {events} from '@/api'
import type {FederatedEvent} from '@/api/events'

const {t} = useI18n()
const federatedEvents = ref<FederatedEvent[]>([])

const pad2 = (n: number) => String(n).padStart(2, '0')

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

onMounted(async () => {
  try {
    federatedEvents.value = await events.listFederatedEvents()
  } catch { /* ignore — no federation partners */ }
})
</script>

<template>
  <div v-if="federatedEvents.length > 0" class="space-y-3">
    <SectionHeader>
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-2"/>
      {{ t('eventsUpcoming.federated') }}
    </SectionHeader>
    <div class="space-y-2">
      <router-link
          v-for="fed in federatedEvents"
          :key="`fed-${fed.partnerId}-${fed.event.id}`"
          :to="{ name: 'federated-event-detail', params: { stationUid: fed.partnerStationUid, eventId: fed.event.id } }"
          class="block"
      >
      <NeutralContainer class="space-y-1 hover:border-(--accent) transition-colors">
        <div class="flex items-center justify-between flex-wrap gap-2">
          <div>
            <span class="font-medium">{{ fed.event.name }}</span>
            <SecondaryBadge class="ml-2">{{ fed.partnerStationName }}</SecondaryBadge>
            <MutedText size="sm" class="ml-2" v-if="fed.event.startTime">{{ formatTime(fed.event.startTime) }} – {{ formatTime(fed.event.endTime) }}</MutedText>
          </div>
          <InfoBadge v-if="fed.event.requiresRegistration">{{ t('eventsUpcoming.registrationRequired') }}</InfoBadge>
        </div>
        <p v-if="fed.event.description" class="text-sm text-(--text-muted)">{{ fed.event.description }}</p>
      </NeutralContainer>
      </router-link>
    </div>
  </div>
</template>
