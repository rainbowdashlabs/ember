/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FederatedRegistrantSummary from './FederatedRegistrantSummary.vue'
import type {EnrichedFederationRegistration, EventRegistrationFieldValue, EventRegistrationField, PartnerPlacesView} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import type {QueueEntry} from '@/util/registrationQueue'
import {events} from '@/api'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {describeFailure, type Failure} from '@/util/failure'

const props = defineProps<{
  registrations: EnrichedFederationRegistration[]
  /** What each partner may do here, by partner. Absent means this station decides, as it always did. */
  partnerPlaces: PartnerPlacesView[]
  /** Whether the appointment is a part of an event group, whose partner sign-ups are decided on the event group. */
  eventGroupPart?: boolean
  /** The waiting list of the date, which says where a partner's member waiting on it stands. */
  queue?: QueueEntry[]
  /** Whether the appointment keeps a waiting list, which only one with a participant limit does. */
  queueOpen?: boolean
  /** Whether an action on the list is under way. */
  busy?: boolean
  /** The questions the appointment asks, whose answers each row shows. */
  fields?: EventRegistrationField[]
}>()

const {t} = useI18n()

/**
 * What partners' members answered, by their registration. Read again whenever the list or the questions
 * change, once the list has partner sign-ups and the appointment asks anything. An answer that arrives
 * after a newer read was started is dropped, and answers that cannot be read are said above the list.
 */
const answersOf = ref(new Map<number, EventRegistrationFieldValue[]>())
const answersFailure = ref<Failure | null>(null)
let answersRead = 0

watch(() => [props.registrations, props.fields] as const, async ([registrations, fields]) => {
  const read = ++answersRead
  const first = registrations[0]
  answersFailure.value = null
  if (!first || !fields?.length) {
    answersOf.value = new Map()
    return
  }
  try {
    const listed = await events.listPartnerAnswers(first.registration.eventId)
    if (read === answersRead) answersOf.value = new Map(listed.map(entry => [entry.registrationId, entry.answers]))
  } catch (e) {
    if (read === answersRead) answersFailure.value = describeFailure(e, t)
  }
}, {immediate: true})

/**
 * Whether this station still decides about a given partner's people.
 *
 * <p>Where it has handed the decision over, the buttons go and a line says who is choosing instead.
 * The server refuses either way; a row that simply refused when pressed would leave the reader
 * guessing why.
 */
function partnerDecides(partnerId: number): boolean {
  return props.partnerPlaces.some(place => place.partnerId === partnerId && place.partnerConfirms)
}

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
  queue: [registrationId: number]
}>()

/**
 * Whether this station decides about a sign-up here: it waits for a decision or on the waiting list,
 * the appointment is no part of an event group, and the partner has not kept the decision.
 */
function decidable(registration: EnrichedFederationRegistration['registration']): boolean {
  const open = registration.status === RegistrationStatus.PENDING || registration.status === RegistrationStatus.QUEUED
  return open && !props.eventGroupPart && !partnerDecides(registration.partnerId)
}

</script>

<template>
  <NeutralContainer v-if="registrations.length > 0" class="space-y-3">
    <SubHeader>{{ t('eventDetail.federatedRegistrations') }}</SubHeader>
    <FailureAlert :failure="answersFailure"/>
    <div class="space-y-2">
      <NeutralContainer v-for="fr in registrations" :key="fr.registration.id" class="flex items-center justify-between gap-2">
        <FederatedRegistrantSummary :registration="fr" :fields="fields ?? []" :answers="answersOf.get(fr.registration.id)" :queue="queue"/>
        <MutedText v-if="eventGroupPart && fr.registration.status === RegistrationStatus.PENDING" size="sm" data-testid="federated-decided-on-group">
          {{ t('eventGroups.decidedOnEventGroup') }}
        </MutedText>
        <MutedText v-else-if="partnerDecides(fr.registration.partnerId)" size="sm">
          {{ t('eventDetail.federatedPartnerDecides') }}
        </MutedText>
        <ButtonRow v-else-if="decidable(fr.registration)" :pair="!queueOpen">
          <PrimaryButton compact :disabled="busy" :data-testid="`federated-accept-${fr.registration.id}`" @click="emit('accept', fr.registration.id)">
            <font-awesome-icon :icon="['fas', fr.registration.status === RegistrationStatus.QUEUED ? 'circle-up' : 'check']" class="mr-1"/>
            {{ fr.registration.status === RegistrationStatus.QUEUED ? t('registrationQueue.moveUp') : t('eventsRegistrations.accept') }}
          </PrimaryButton>
          <ErrorButton compact :disabled="busy" @click="emit('deny', fr.registration.id)">
            <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
            {{ t('eventsRegistrations.deny') }}
          </ErrorButton>
          <SecondaryButton
              v-if="queueOpen && fr.registration.status === RegistrationStatus.PENDING"
              compact
              :disabled="busy"
              :icon="['fas', 'list-ol']"
              :data-testid="`federated-queue-${fr.registration.id}`"
              @click="emit('queue', fr.registration.id)"
          >
            {{ t('registrationQueue.putOnList') }}
          </SecondaryButton>
        </ButtonRow>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
