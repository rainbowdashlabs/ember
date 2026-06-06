/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import { events } from '@/api'
import type { FederatedEvent, FederatedRegistration } from '@/api/events'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const { sessionInfo } = useSession()

const federatedEvents = ref<FederatedEvent[]>([])
const myRegistrations = ref<FederatedRegistration[]>([])
const registering = ref<string | null>(null)

const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])
const currentMemberUid = computed(() => sessionInfo.value?.member?.uid ?? '')

const pad2 = (n: number) => String(n).padStart(2, '0')

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${pad2(d.getHours())}:${pad2(d.getMinutes())}`
}

function getEventDate(fed: FederatedEvent): string {
  if (fed.event.startTime) return new Date(fed.event.startTime).toISOString().split('T')[0]
  return new Date().toISOString().split('T')[0]
}

function getRegistration(eventId: number, memberUid: string): FederatedRegistration | undefined {
  return myRegistrations.value.find(r => r.eventId === eventId && r.remoteMemberId === memberUid)
}

function isRegistered(eventId: number, memberUid: string): boolean {
  return !!getRegistration(eventId, memberUid)
}

// Eligible members for registration: self + managed
const eligibleMembers = computed(() => {
  const result: { uid: string; name: string }[] = []
  if (currentMemberUid.value) {
    result.push({ uid: currentMemberUid.value, name: t('eventsUpcoming.myself') })
  }
  for (const m of managedMembers.value) {
    if (m.uid) result.push({ uid: m.uid, name: m.name ?? m.email ?? `#${m.id}` })
  }
  return result
})

function membersWithoutRegistration(eventId: number) {
  return eligibleMembers.value.filter(m => !isRegistered(eventId, m.uid))
}

// Per-event selected member for multi-member selector
const selectedMemberUids = ref<Record<string, string>>({})

function getSelectedUid(eventKey: string, eventId: number): string | null {
  const unregistered = membersWithoutRegistration(eventId)
  if (unregistered.length === 1) return unregistered[0].uid
  return selectedMemberUids.value[eventKey] || null
}

async function register(fed: FederatedEvent, memberUid: string) {
  const key = `${fed.partnerStationUid}-${fed.event.id}`
  registering.value = key
  try {
    await events.registerForFederatedEvent(fed.partnerStationUid, fed.event.id, getEventDate(fed), memberUid)
    myRegistrations.value.push({
      eventId: fed.event.id,
      remoteMemberId: memberUid,
      eventDate: getEventDate(fed),
      status: 'PENDING',
      partnerId: fed.partnerId,
    })
  } catch { /* error */ }
  registering.value = null
}

async function withdraw(fed: FederatedEvent, memberUid: string) {
  const key = `${fed.partnerStationUid}-${fed.event.id}`
  registering.value = key
  try {
    await events.withdrawFederatedRegistration(fed.partnerStationUid, fed.event.id, getEventDate(fed), memberUid)
    myRegistrations.value = myRegistrations.value.filter(
        r => !(r.eventId === fed.event.id && r.remoteMemberId === memberUid))
  } catch { /* error */ }
  registering.value = null
}

onMounted(async () => {
  try {
    const [fedEvents, regs] = await Promise.all([
      events.listFederatedEvents(),
      events.listMyFederatedRegistrations().catch(() => []),
    ])
    federatedEvents.value = fedEvents
    myRegistrations.value = regs
  } catch { /* ignore */ }
})
</script>

<template>
  <div v-if="federatedEvents.length > 0" class="space-y-3">
    <SectionHeader>
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-2"/>
      {{ t('eventsUpcoming.federated') }}
    </SectionHeader>
    <div class="space-y-2">
      <NeutralContainer
          v-for="fed in federatedEvents"
          :key="`fed-${fed.partnerId}-${fed.event.id}`"
          class="space-y-2"
      >
        <div
            class="cursor-pointer"
            @click="router.push({ name: 'federated-event-detail', params: { stationUid: fed.partnerStationUid, eventId: fed.event.id } })"
        >
          <div class="flex items-center justify-between flex-wrap gap-2">
            <div>
              <span class="font-medium">{{ fed.event.name }}</span>
              <SecondaryBadge class="ml-2">{{ fed.partnerStationName }}</SecondaryBadge>
              <MutedText size="sm" class="ml-2" v-if="fed.event.startTime">{{ formatTime(fed.event.startTime) }} – {{ formatTime(fed.event.endTime) }}</MutedText>
            </div>
            <InfoBadge v-if="fed.event.requiresRegistration">{{ t('eventsUpcoming.registrationRequired') }}</InfoBadge>
          </div>
          <p v-if="fed.event.description" class="text-sm text-(--text-muted)">{{ fed.event.description }}</p>
        </div>

        <!-- Registration actions -->
        <div v-if="fed.event.requiresRegistration" class="flex items-center gap-2 flex-wrap pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
          <!-- Show existing registrations -->
          <template v-for="m in eligibleMembers" :key="`reg-${fed.event.id}-${m.uid}`">
            <div v-if="getRegistration(fed.event.id, m.uid)" class="flex items-center gap-1">
              <span v-if="eligibleMembers.length > 1" class="text-xs text-(--text-muted)">{{ m.name }}:</span>
              <SuccessBadge v-if="getRegistration(fed.event.id, m.uid)!.status === 'ACCEPTED'">{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
              <InfoBadge v-else-if="getRegistration(fed.event.id, m.uid)!.status === 'PENDING'">{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
              <ErrorBadge v-else-if="getRegistration(fed.event.id, m.uid)!.status === 'DENIED'">{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
              <ErrorButton compact class="text-xs" @click.stop="withdraw(fed, m.uid)">
                <font-awesome-icon :icon="['fas', 'xmark']" />
              </ErrorButton>
            </div>
          </template>

          <!-- Register new -->
          <template v-if="membersWithoutRegistration(fed.event.id).length > 0">
            <SelectInput
                v-if="membersWithoutRegistration(fed.event.id).length > 1"
                v-model="selectedMemberUids[`${fed.partnerStationUid}-${fed.event.id}`]"
                class="text-sm w-40"
                @click.stop
            >
              <option disabled value="">{{ t('eventsUpcoming.selectMember') }}</option>
              <option v-for="m in membersWithoutRegistration(fed.event.id)" :key="m.uid" :value="m.uid">{{ m.name }}</option>
            </SelectInput>

            <PrimaryButton
                :disabled="registering != null || !getSelectedUid(`${fed.partnerStationUid}-${fed.event.id}`, fed.event.id)"
                compact
                class="text-sm"
                @click.stop="() => { const uid = getSelectedUid(`${fed.partnerStationUid}-${fed.event.id}`, fed.event.id); if (uid) register(fed, uid) }"
            >
              <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
              {{ t('eventsUpcoming.register') }}
            </PrimaryButton>
          </template>
        </div>
      </NeutralContainer>
    </div>
  </div>
</template>
