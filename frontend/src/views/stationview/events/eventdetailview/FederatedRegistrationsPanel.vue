/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import type {EnrichedFederationRegistration, PartnerPlacesView} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import {formatDate} from '@/util/format'
import {partnerQueuePosition, type QueueEntry} from '@/util/registrationQueue'

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
}>()

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

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="registrations.length > 0" class="space-y-3">
    <SubHeader>{{ t('eventDetail.federatedRegistrations') }}</SubHeader>
    <div class="space-y-2">
      <NeutralContainer v-for="fr in registrations" :key="fr.registration.id" class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <MemberName v-if="fr.memberIdentity" :identity="fr.memberIdentity"/>
          <MutedText size="sm">{{ formatDate(fr.registration.eventDate) }}</MutedText>
          <SuccessBadge v-if="fr.registration.status === RegistrationStatus.ACCEPTED">{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
          <InfoBadge v-else-if="fr.registration.status === RegistrationStatus.PENDING">{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
          <ErrorBadge v-else-if="fr.registration.status === RegistrationStatus.DENIED">{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
          <RegistrationStatusBadge
              v-else-if="fr.registration.status === RegistrationStatus.QUEUED"
              :status="fr.registration.status"
              :queue-position="partnerQueuePosition(queue ?? [], fr.registration.id)"/>
          <ErrorBadge v-if="fr.registration.agreementWithdrawnAt" data-testid="federated-agreement-withdrawn">
            {{ t('eventDetail.federatedAgreementWithdrawn') }}
          </ErrorBadge>
        </div>
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
