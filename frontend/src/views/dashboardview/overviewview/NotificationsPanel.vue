/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {notifications} from '@/api'
import type {NotificationEntry} from '@/api/types'

const {t} = useI18n()
const router = useRouter()

const notifs = ref<NotificationEntry[]>([])
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

function renderMessage(n: NotificationEntry): string {
  const params = {...n.params}
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
    router.push({name: n.link.route, params: n.link.routeParams})
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

async function ack(id: number) {
  await notifications.acknowledge(id)
  notifs.value = notifs.value.filter(n => n.id !== id)
}

async function ackAll() {
  await notifications.acknowledgeAll()
  notifs.value = []
}

async function loadData() {
  loading.value = true
  try {
    notifs.value = await notifications.listUnacknowledged()
  } catch { /* ignore */ }
  loading.value = false
}

onMounted(loadData)
</script>

<template>
  <NeutralContainer class="flex flex-col max-h-[66vh]">
    <div class="flex items-center justify-between mb-4 shrink-0">
      <SectionHeader>
        <font-awesome-icon :icon="['fas', 'bell']" class="mr-2"/>
        {{ t('dashboard.notifications') }}
        <span v-if="notifs.length > 0"> ({{ notifs.length }})</span>
      </SectionHeader>
      <SecondaryButton :icon="['fas', 'check-double']" v-if="notifs.length > 0" class="text-sm" @click="ackAll">
        {{ t('dashboard.acknowledgeAll') }}
      </SecondaryButton>
    </div>

    <div class="overflow-y-auto flex-1 space-y-2">
      <EmptyState compact v-if="!loading && notifs.length === 0">
        <font-awesome-icon :icon="['fas', 'check-double']" class="text-2xl text-success mb-2"/>
        <p>{{ t('dashboard.noNotifications') }}</p>
      </EmptyState>

      <template v-if="notifs.length > 0">
        <NeutralContainer v-for="n in notifs" :key="n.id" class="flex items-start justify-between gap-3 py-2 px-3"
                          :class="{ 'cursor-pointer hover:bg-(--bg-accent)': n.link }" @click="navigateTo(n)">
          <div class="flex items-start gap-3">
            <font-awesome-icon :icon="['fas', typeIcons[n.type] ?? 'bell']"
                               class="text-primary mt-0.5 h-4 w-4 shrink-0"/>
            <div>
              <span class="text-xs font-semibold text-(--text-muted)">{{ t(`notification.typeLabel.${n.type}`) }}</span>
              <p class="text-sm">{{ renderMessage(n) }}</p>
              <p v-if="renderDetail(n)" class="text-xs text-(--text-muted) italic">{{ renderDetail(n) }}</p>
              <p class="text-xs text-(--text-muted)">{{ formatDate(n.createdAt) }}</p>
            </div>
          </div>
          <LinkButton class="shrink-0 mt-1" @click="($event: MouseEvent) => { $event.stopPropagation(); ack(n.id) }">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-0.5"/>
            {{ t('dashboard.acknowledge') }}
          </LinkButton>
        </NeutralContainer>
      </template>
    </div>
  </NeutralContainer>
</template>
