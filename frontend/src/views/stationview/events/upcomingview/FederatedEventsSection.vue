/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import FederatedEventTile from '@/views/stationview/events/upcomingview/federatedeventssection/FederatedEventTile.vue'
import {events} from '@/api'
import type {FederatedEvent, FederatedRegistration} from '@/api/events'
import {useSession} from '@/composables/useSession'
import {reportCaughtError} from '@/util/devErrorReporter'

const {t} = useI18n()
const {sessionInfo} = useSession()

const federatedEvents = ref<FederatedEvent[]>([])
const myRegistrations = ref<FederatedRegistration[]>([])
const registering = ref<string | null>(null)

const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])
const currentMemberUid = computed(() => sessionInfo.value?.member?.uid ?? '')

const eligibleMembers = computed(() => {
  const result: { uid: string; name: string }[] = []
  if (currentMemberUid.value) {
    result.push({uid: currentMemberUid.value, name: t('eventsUpcoming.myself')})
  }
  for (const m of managedMembers.value) {
    if (m.uid) result.push({uid: m.uid, name: m.name ?? m.email ?? `#${m.id}`})
  }
  return result
})

function getEventDate(fed: FederatedEvent): string {
  if (fed.event.startTime) return new Date(fed.event.startTime).toISOString().slice(0, 10)
  return new Date().toISOString().slice(0, 10)
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
  } catch (e) {
    reportCaughtError(e, 'federated event registration')
  }
  registering.value = null
}

async function withdraw(fed: FederatedEvent, memberUid: string) {
  const key = `${fed.partnerStationUid}-${fed.event.id}`
  registering.value = key
  try {
    await events.withdrawFederatedRegistration(fed.partnerStationUid, fed.event.id, getEventDate(fed), memberUid)
    myRegistrations.value = myRegistrations.value.filter(
        r => !(r.eventId === fed.event.id && r.remoteMemberId === memberUid))
  } catch (e) {
    reportCaughtError(e, 'federated event withdrawal')
  }
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
  } catch (e) {
    reportCaughtError(e, 'federated event listing')
  }
})
</script>

<template>
  <div v-if="federatedEvents.length > 0" class="space-y-3">
    <SubHeader>
      <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="mr-2"/>
      {{ t('eventsUpcoming.federated') }}
    </SubHeader>
    <div class="space-y-2">
      <FederatedEventTile
          v-for="fed in federatedEvents"
          :key="`fed-${fed.partnerId}-${fed.event.id}`"
          :fed="fed"
          :eligible-members="eligibleMembers"
          :registrations="myRegistrations"
          :registering="registering"
          @register="register"
          @withdraw="withdraw"
      />
    </div>
  </div>
</template>
