/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {events, stationMembers} from '@/api'
import type {StationEvent, StationMember} from '@/api/types'
import {RegistrationStatus} from '@/api/types'
import {useStations} from '@/composables/useStations'
import MemberName from '@/components/avatar/MemberName.vue'

const {t} = useI18n()
const {currentStationId} = useStations()

interface PendingRegistration {
  id: number
  eventId: number
  memberId: number
  eventDate: string
  status: string
  createdAt: string
}

const registrations = ref<PendingRegistration[]>([])
const allEvents = ref<StationEvent[]>([])
const allMembers = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')

function eventName(eventId: number): string {
  return allEvents.value.find(e => e.id === eventId)?.name ?? `#${eventId}`
}

function memberName(memberId: number): string {
  const m = allMembers.value.find(m => m.id === memberId)
  if (!m) return `#${memberId}`
  return m.name && m.name.trim() ? m.name : m.email ?? `#${memberId}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [regs, evs, members] = await Promise.all([
      events.listPendingRegistrations(),
      events.listEvents(),
      stationMembers.listMembers(currentStationId.value!),
    ])
    registrations.value = regs
    allEvents.value = evs
    allMembers.value = members
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function accept(id: number) {
  error.value = ''
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.ACCEPTED)
    registrations.value = registrations.value.filter(r => r.id !== id)
  } catch {
    error.value = t('common.error')
  }
}

async function deny(id: number) {
  error.value = ''
  try {
    await events.updateRegistrationStatus(id, RegistrationStatus.DENIED)
    registrations.value = registrations.value.filter(r => r.id !== id)
  } catch {
    error.value = t('common.error')
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <SectionHeader>{{ t('eventsRegistrations.title') }}</SectionHeader>

        <div v-if="registrations.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('eventsRegistrations.empty') }}
        </div>

        <div class="space-y-3">
          <NeutralContainer v-for="reg in registrations" :key="reg.id" class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
            <div class="flex items-center gap-2 flex-wrap text-sm">
              <MemberName :name="memberName(reg.memberId)" :member-id="reg.memberId"/>
              <span class="text-(--text-muted)">{{ eventName(reg.eventId) }}</span>
              <span class="text-xs text-(--text-muted)">{{ reg.eventDate }}</span>
            </div>
            <div class="flex items-center gap-2">
              <PrimaryButton @click="accept(reg.id)">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
                {{ t('eventsRegistrations.accept') }}
              </PrimaryButton>
              <ErrorButton @click="deny(reg.id)">
                <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
                {{ t('eventsRegistrations.deny') }}
              </ErrorButton>
            </div>
          </NeutralContainer>
        </div>
      </template>
    </div>
  </ViewContent>
</template>
