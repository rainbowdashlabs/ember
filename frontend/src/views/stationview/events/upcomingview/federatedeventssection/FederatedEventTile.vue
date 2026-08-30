/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import EventHeadline from '@/views/stationview/events/eventshared/EventHeadline.vue'
import EventRegistrationActions from '@/views/stationview/events/eventshared/EventRegistrationActions.vue'
import type {FederatedEvent, FederatedRegistration, RegistrationStatusName} from '@/api/events'
import {formatTime} from '@/util/format'
import type {AnswerablePerson, GivenAnswer} from '@/util/eventAnswers'

const props = defineProps<{
  fed: FederatedEvent
  eligibleMembers: AnswerablePerson<string>[]
  registrations: FederatedRegistration[]
  registering: string | null
}>()

const emit = defineEmits<{
  register: [fed: FederatedEvent, people: AnswerablePerson<string>[]]
  withdraw: [fed: FederatedEvent, memberUid: string]
}>()

const {t} = useI18n()
const router = useRouter()

/**
 * What the household has answered about the partner's appointment.
 *
 * <p>Taking one back names the person rather than a row: the registration lives at the partner
 * station, and this station only knows who it belongs to.
 */
const answers = computed((): GivenAnswer<string, string>[] => {
  const given: GivenAnswer<string, string>[] = []
  for (const person of props.eligibleMembers) {
    const registration = props.registrations.find(
        entry => entry.eventId === props.fed.event.id && entry.remoteMemberId === person.key)
    if (!registration) continue
    given.push({
      key: person.key,
      name: person.name,
      status: registration.status as RegistrationStatusName,
      undo: person.key,
    })
  }
  return given
})

/**
 * The day the partner's appointment falls on, taken off its start.
 *
 * <p>A federated appointment carries an instant rather than the occurrence dates a local one is
 * expanded into, so the tile reads the date out of it and shows the same line every other
 * appointment in the list shows.
 */
const occurrenceDate = computed(() => props.fed.event.startTime?.slice(0, 10) ?? '')

/** Where the partner's own page for this appointment lives. */
const detailRoute = computed(() => ({
  name: 'federated-event-detail',
  params: {stationUid: props.fed.partnerStationUid, eventId: props.fed.event.id},
}))

function openDetail() {
  router.push(detailRoute.value)
}
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="federated-event">
    <div class="cursor-pointer" @click="openDetail">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <EventHeadline
            :name="fed.event.name" :to="detailRoute" :date="occurrenceDate" :end-date="null"
            :start-time="fed.event.startTime" :end-time="fed.event.endTime" :format-time="formatTime">
          <SecondaryBadge>{{ fed.partnerStationName }}</SecondaryBadge>
          <InfoBadge v-if="fed.event.requiresRegistration">{{ t('eventsUpcoming.registrationRequired') }}</InfoBadge>
        </EventHeadline>
      </div>
      <p v-if="fed.event.description" class="text-sm text-(--text-muted)">{{ fed.event.description }}</p>
    </div>

    <EventRegistrationActions
        v-if="fed.event.requiresRegistration"
        :people="eligibleMembers"
        :answers="answers"
        :requires-registration="true"
        :has-managed-members="eligibleMembers.length > 1"
        :registering="registering != null"
        @register="people => emit('register', fed, people)"
        @withdraw="uid => emit('withdraw', fed, uid)"
    />
  </NeutralContainer>
</template>
