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
import {UNDO_WINDOW_MS, type FederatedEvent, type FederatedRegistration} from '@/api/events'
import {showToast} from '@/util/toast'
import {useSession} from '@/composables/useSession'
import {reportCaughtError} from '@/util/devErrorReporter'
import type {AnswerablePerson} from '@/util/eventAnswers'

const {t} = useI18n()
const {sessionInfo} = useSession()

const federatedEvents = ref<FederatedEvent[]>([])
const myRegistrations = ref<FederatedRegistration[]>([])
const registering = ref<string | null>(null)

const managedMembers = computed(() => sessionInfo.value?.managedMembers ?? [])
const currentMemberUid = computed(() => sessionInfo.value?.member?.uid ?? '')

const eligibleMembers = computed((): AnswerablePerson<string>[] => {
  const result: AnswerablePerson<string>[] = []
  if (currentMemberUid.value) {
    result.push({key: currentMemberUid.value, name: t('eventsUpcoming.myself')})
  }
  for (const m of managedMembers.value) {
    if (m.uid) result.push({key: m.uid, name: m.name ?? m.email ?? `#${m.id}`})
  }
  return result
})

function getEventDate(fed: FederatedEvent): string {
  if (fed.event.startTime) return new Date(fed.event.startTime).toISOString().slice(0, 10)
  return new Date().toISOString().slice(0, 10)
}

/** Signing the chosen people up, one request each, the way the station's own appointments do. */
async function register(fed: FederatedEvent, people: AnswerablePerson<string>[]) {
  const key = `${fed.partnerStationUid}-${fed.event.id}`
  registering.value = key
  try {
    for (const person of people) {
      await events.registerForFederatedEvent(fed.partnerStationUid, fed.event.id, getEventDate(fed), person.key)
      myRegistrations.value.push({
        eventId: fed.event.id,
        remoteMemberId: person.key,
        eventDate: getEventDate(fed),
        status: 'PENDING',
        partnerId: fed.partnerId,
      })
    }
  } catch (e) {
    reportCaughtError(e, 'federated event registration')
  }
  registering.value = null
}

/**
 * Giving up a place at a partner station's appointment, and offering it back.
 *
 * <p>How long the offer stands is the other station's to say, because the registration is theirs.
 * This end simply asks, and is told no once the few minutes have passed.
 */
async function withdraw(fed: FederatedEvent, memberUid: string) {
  const key = `${fed.partnerStationUid}-${fed.event.id}`
  registering.value = key
  try {
    await events.withdrawFederatedRegistration(fed.partnerStationUid, fed.event.id, getEventDate(fed), memberUid)
    myRegistrations.value = myRegistrations.value.filter(
        r => !(r.eventId === fed.event.id && r.remoteMemberId === memberUid))
    showToast(t('eventsUpcoming.signedOff'), 'info', UNDO_WINDOW_MS, {
      label: t('eventsUpcoming.undoSignOff'),
      run: () => undoWithdrawal(fed, memberUid),
    })
  } catch (e) {
    reportCaughtError(e, 'federated event withdrawal')
  }
  registering.value = null
}

async function undoWithdrawal(fed: FederatedEvent, memberUid: string) {
  const key = `${fed.partnerStationUid}-${fed.event.id}`
  registering.value = key
  try {
    await events.undoFederatedWithdrawal(fed.partnerStationUid, fed.event.id, getEventDate(fed), memberUid)
    myRegistrations.value = await events.listMyFederatedRegistrations()
  } catch {
    showToast(t('eventsUpcoming.undoTooLate'), 'error')
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
