/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type { NotificationEntry, ExchangeRequestEntry, StationEvent } from '@/api/types'
import { RegistrationStatus, ExchangeStatus } from '@/api/types'
import { notifications, exchanges, events } from '@/api'
import type { EventRegistrationEntry } from '@/api/events'
import { StationModules } from '@/api/types'
import { useSession } from '@/composables/useSession'
import ErrorContainer from '@/components/container/ErrorContainer.vue'

const { t } = useI18n()
const router = useRouter()
const { isGuardian, isModuleEnabled, sessionInfo } = useSession()

const profileIncomplete = computed(() => sessionInfo.value?.profileComplete === false)

function isOtherMember(memberId: number): boolean {
  return isGuardian() && memberId !== (sessionInfo.value?.member?.id ?? 0)
}

const notifs = ref<NotificationEntry[]>([])
const exchangeList = ref<ExchangeRequestEntry[]>([])
const registrations = ref<EventRegistrationEntry[]>([])
const allEvents = ref<StationEvent[]>([])
const loading = ref(true)

const typeIcons: Record<string, string> = {
  NEW_NEWS: 'newspaper',
  NEWS_COMMENT: 'comment',
  EVENT_REGISTRATION_STATUS: 'calendar-days',
  EXCHANGE_STATUS_CHANGE: 'rotate',
  EXCHANGE_NEW_REQUEST: 'rotate',
  NEW_EVENT: 'calendar-plus',
  MEMBER_ADDED_TO_GROUP: 'layer-group',
  PROFILE_FIELD_CHANGED: 'user',
  PROCUREMENT_REQUESTED: 'box-open',
  PROCUREMENT_FULFILLED: 'box-open',
  LOST_AND_FOUND_NEW: 'box-open',
  LOST_AND_FOUND_CLAIMED: 'box-open',
}

const openExchanges = computed(() => exchangeList.value.filter(e => e.status !== ExchangeStatus.EXCHANGED))
const activeRegistrations = computed(() => registrations.value.filter(r => r.status !== RegistrationStatus.DECLINED))

function eventName(eventId: number): string {
  return allEvents.value.find(e => e.id === eventId)?.name ?? `#${eventId}`
}

function renderMessage(n: NotificationEntry): string {
  const params = { ...n.params }
  if (n.type === 'EVENT_REGISTRATION_STATUS' && params.status) {
    params.status = t(`dashboard.registrationStatus.${params.status}`)
  }
  if (n.type === 'EXCHANGE_STATUS_CHANGE' && params.status) {
    params.status = t(`dashboard.exchangeStatus.${params.status}`)
  }
  return t(n.localeKey, params)
}

function renderDetail(n: NotificationEntry): string | null {
  if (n.type === 'NEW_NEWS' && n.params?.preview) return n.params.preview
  if (n.type === 'NEWS_COMMENT' && n.params?.preview) return n.params.preview
  if ((n.type === 'EXCHANGE_STATUS_CHANGE' || n.type === 'EXCHANGE_NEW_REQUEST') && n.params?.reason) return n.params.reason
  if ((n.type === 'EVENT_REGISTRATION_STATUS' || n.type === 'NEW_EVENT') && n.params?.eventDescription) return n.params.eventDescription
  return null
}

function navigateTo(n: NotificationEntry) {
  if (n.link) {
    router.push({ name: n.link.route, params: n.link.routeParams })
  }
}

async function loadData() {
  loading.value = true
  try {
    const [n, ex, reg, ev] = await Promise.all([
      notifications.listUnacknowledged(),
      exchanges.listExchanges(),
      events.listMyRegistrations(),
      events.listEvents(),
    ])
    notifs.value = n
    exchangeList.value = ex
    registrations.value = reg
    allEvents.value = ev
  } catch { /* ignore */ }
  loading.value = false
}

async function ack(id: number) {
  await notifications.acknowledge(id)
  notifs.value = notifs.value.filter(n => n.id !== id)
}

async function ackAll() {
  await notifications.acknowledgeAll()
  notifs.value = []
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function statusBadgeComponent(status: string) {
  switch (status) {
    case RegistrationStatus.ACCEPTED: case ExchangeStatus.EXCHANGED: return SuccessBadge
    case RegistrationStatus.PENDING: case ExchangeStatus.ANNOUNCED: case ExchangeStatus.SHIPPED: return InfoBadge
    case RegistrationStatus.DENIED: case RegistrationStatus.DECLINED: return ErrorBadge
    default: return SecondaryBadge
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />

      <template v-if="!loading">
        <!-- Onboarding banner -->
        <ErrorContainer v-if="profileIncomplete" class="flex items-center justify-between gap-4">
          <div>
            <p class="font-semibold text-sm">{{ t('dashboard.profileIncomplete') }}</p>
            <p class="text-xs">{{ t('dashboard.profileIncompleteHint') }}</p>
          </div>
          <PrimaryButton class="shrink-0" @click="router.push({ name: 'profile' })">
            {{ t('dashboard.completeProfile') }}
          </PrimaryButton>
        </ErrorContainer>

        <!-- Tile layout for all panels -->
        <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">

        <!-- Notifications panel -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>
              <font-awesome-icon :icon="['fas', 'bell']" class="mr-2" />
              {{ t('dashboard.notifications') }}
              <span v-if="notifs.length > 0"> ({{ notifs.length }})</span>
            </SectionHeader>
            <SecondaryButton v-if="notifs.length > 0" class="text-sm" @click="ackAll">
              <font-awesome-icon :icon="['fas', 'check-double']" class="mr-1" />
              {{ t('dashboard.acknowledgeAll') }}
            </SecondaryButton>
          </div>

          <div v-if="notifs.length === 0" class="text-center text-(--text-muted) py-4">
            <font-awesome-icon :icon="['fas', 'check-double']" class="text-2xl text-success mb-2" />
            <p>{{ t('dashboard.noNotifications') }}</p>
          </div>

          <div v-else class="space-y-2">
            <NeutralContainer v-for="n in notifs" :key="n.id" class="flex items-start justify-between gap-3 py-2 px-3"
              :class="{ 'cursor-pointer hover:bg-(--bg-accent)': n.link }" @click="navigateTo(n)">
              <div class="flex items-start gap-3">
                <font-awesome-icon :icon="['fas', typeIcons[n.type] ?? 'bell']" class="text-primary mt-0.5 h-4 w-4 shrink-0" />
                <div>
                  <span class="text-xs font-semibold text-(--text-muted)">{{ t(`notification.typeLabel.${n.type}`) }}</span>
                  <p class="text-sm">{{ renderMessage(n) }}</p>
                  <p v-if="renderDetail(n)" class="text-xs text-(--text-muted) italic">{{ renderDetail(n) }}</p>
                  <p class="text-xs text-(--text-muted)">{{ formatDate(n.createdAt) }}</p>
                </div>
              </div>
              <button type="button" class="text-xs text-primary hover:underline shrink-0 mt-1" @click.stop="ack(n.id)">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-0.5" />
                {{ t('dashboard.acknowledge') }}
              </button>
            </NeutralContainer>
          </div>
        </NeutralContainer>

          <!-- Exchange requests panel -->
          <NeutralContainer v-if="isModuleEnabled(StationModules.INVENTORY)" class="space-y-3">
            <SectionHeader>
              <font-awesome-icon :icon="['fas', 'rotate']" class="mr-2" />
              {{ t('dashboard.exchanges') }}
            </SectionHeader>
            <div v-if="openExchanges.length === 0" class="text-center text-(--text-muted) py-4">
              {{ t('dashboard.noExchanges') }}
            </div>
            <div v-else class="space-y-2">
              <NeutralContainer v-for="ex in openExchanges" :key="ex.id"
                class="flex items-center justify-between gap-3 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
                @click="router.push({ name: 'inventory-exchanges' })">
                <div>
                  <MemberName v-if="isOtherMember(ex.memberId)" :name="ex.memberName" class="text-xs font-semibold text-primary"/>
                  <p class="text-sm font-medium">{{ ex.inventoryName }}</p>
                  <p class="text-xs text-(--text-muted)">{{ ex.oldSizeLabel ?? t('common.unisize') }} → {{ ex.newSizeLabel ?? t('common.unisize') }}</p>
                  <p class="text-xs text-(--text-muted)">{{ ex.reason }}</p>
                </div>
                <component :is="statusBadgeComponent(ex.status)">
                  {{ t(`dashboard.exchangeStatus.${ex.status}`) }}
                </component>
              </NeutralContainer>
            </div>
          </NeutralContainer>

          <!-- Event registrations panel -->
          <NeutralContainer v-if="isModuleEnabled(StationModules.EVENTS)" class="space-y-3">
            <SectionHeader>
              <font-awesome-icon :icon="['fas', 'calendar-days']" class="mr-2" />
              {{ isGuardian() ? t('dashboard.registrationsManaged') : t('dashboard.registrations') }}
            </SectionHeader>
            <div v-if="activeRegistrations.length === 0" class="text-center text-(--text-muted) py-4">
              {{ t('dashboard.noRegistrations') }}
            </div>
            <div v-else class="space-y-2">
              <NeutralContainer v-for="reg in activeRegistrations" :key="reg.id"
                class="flex items-center justify-between gap-3 py-2 px-3 cursor-pointer hover:bg-(--bg-accent)"
                @click="router.push({ name: 'events-upcoming' })">
                <div>
                  <MemberName v-if="isOtherMember(reg.memberId)" :name="reg.memberName" class="text-xs font-semibold text-primary"/>
                  <p class="text-sm font-medium">{{ eventName(reg.eventId) }}</p>
                  <p class="text-xs text-(--text-muted)">{{ reg.eventDate }}</p>
                </div>
                <component :is="statusBadgeComponent(reg.status)">
                  {{ t(`dashboard.registrationStatus.${reg.status}`) }}
                </component>
              </NeutralContainer>
            </div>
          </NeutralContainer>
        </div>

      </template>
    </div>
  </ViewContent>
</template>
