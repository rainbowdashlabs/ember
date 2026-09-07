/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, type RouteLocationRaw} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import {
  isRecurringEvent,
  RegistrationStatus,
  type EventCategory,
  type EventRegistrationEntry,
  type StationEvent,
} from '@/api/events'
import {events} from '@/api'
import {useSession} from '@/composables/useSession'
import {formatDate} from '@/util/format'

const {t} = useI18n()
const router = useRouter()
const {isGuardian, sessionInfo} = useSession()

const registrations = ref<EventRegistrationEntry[]>([])
const allEvents = ref<StationEvent[]>([])
const categories = ref<EventCategory[]>([])

/** The category the registration's event was put in, absent where it was put in none. */
function categoryOf(eventId: number): EventCategory | undefined {
  const categoryId = allEvents.value.find(e => e.id === eventId)?.categoryId
  return categoryId != null ? categories.value.find(cat => cat.id === categoryId) : undefined
}

const activeRegistrations = computed(() => registrations.value.filter(r => r.status !== RegistrationStatus.DECLINED))

function isOtherMember(memberId: number): boolean {
  return isGuardian() && memberId !== (sessionInfo.value?.member?.id ?? 0)
}

function eventName(eventId: number): string {
  return allEvents.value.find(e => e.id === eventId)?.name ?? `#${eventId}`
}

/**
 * Date-aware deep link: recurring events must carry the occurrence date so the detail page
 * lands on the right instance, not always the first one.
 */
function registrationRoute(reg: EventRegistrationEntry): RouteLocationRaw {
  const event = allEvents.value.find(e => e.id === reg.eventId)
  if (event && isRecurringEvent(event.eventType) && reg.eventDate) {
    return {name: 'event-detail-date', params: {id: reg.eventId, date: reg.eventDate}}
  }
  return {name: 'event-detail', params: {id: reg.eventId}}
}

function statusBadgeComponent(status: string) {
  switch (status) {
    case RegistrationStatus.ACCEPTED: return SuccessBadge
    case RegistrationStatus.PENDING: return InfoBadge
    case RegistrationStatus.DENIED: case RegistrationStatus.DECLINED: return ErrorBadge
    default: return SecondaryBadge
  }
}

async function loadData() {
  try {
    const [reg, ev, cats] = await Promise.all([
      events.listMyRegistrations(),
      events.listEvents(),
      events.listCategories().catch(() => []),
    ])
    registrations.value = reg
    allEvents.value = ev
    categories.value = cats
  } catch { /* ignore */ }
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer class="flex flex-col max-h-[66vh]">
    <SectionHeader class="mb-3 shrink-0">
      <font-awesome-icon :icon="['fas', 'calendar-days']" class="mr-2"/>
      {{ isGuardian() ? t('dashboard.registrationsManaged') : t('dashboard.registrations') }}
    </SectionHeader>
    <div class="overflow-y-auto flex-1 space-y-2">
      <EmptyState compact v-if="activeRegistrations.length === 0">{{ t('dashboard.noRegistrations') }}</EmptyState>
      <template v-else>
        <NeutralContainer v-for="reg in activeRegistrations" :key="reg.id"
                          class="flex items-center justify-between gap-2 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
                          @click="router.push(registrationRoute(reg))">
          <div>
            <MemberName v-if="isOtherMember(reg.memberId)" :identity="reg.memberIdentity ?? null"
                        class="text-xs font-semibold text-primary"/>
            <div class="flex items-center gap-2 min-w-0">
              <p class="truncate text-sm font-medium">{{ eventName(reg.eventId) }}</p>
              <ColorBadge v-if="categoryOf(reg.eventId)" :color="categoryOf(reg.eventId)!.color"
                          class="shrink-0" data-testid="dashboard-event-category">
                {{ categoryOf(reg.eventId)!.name }}
              </ColorBadge>
            </div>
            <p class="text-xs text-(--text-muted)">{{ formatDate(reg.eventDate) }}</p>
          </div>
          <component :is="statusBadgeComponent(reg.status)">
            {{ t(`dashboard.registrationStatus.${reg.status}`) }}
          </component>
        </NeutralContainer>
      </template>
    </div>
  </NeutralContainer>
</template>
