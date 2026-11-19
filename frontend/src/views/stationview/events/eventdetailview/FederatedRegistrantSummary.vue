/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import RegistrationStatusBadge from '../eventshared/RegistrationStatusBadge.vue'
import RegistrationFieldAnswers from './RegistrationFieldAnswers.vue'
import type {EnrichedFederationRegistration, EventRegistrationField, EventRegistrationFieldValue} from '@/api/generated/schema'
import {RegistrationStatus} from '@/api/generated/schema'
import {formatDate} from '@/util/format'
import {partnerQueuePosition, type QueueEntry} from '@/util/registrationQueue'

/**
 * Who a partner's member is, on which date, where their sign-up stands and what they answered to the
 * appointment's questions, as one row of the partner sign-ups lists them.
 */
const props = defineProps<{
  registration: EnrichedFederationRegistration
  /** The questions the appointment asks, which name the answers. */
  fields: EventRegistrationField[]
  /** What this member answered, where they answered anything. */
  answers?: EventRegistrationFieldValue[]
  /** The waiting list of the date, which says where a member waiting on it stands. */
  queue?: QueueEntry[]
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-1">
    <div class="flex items-center gap-2">
      <MemberName v-if="props.registration.memberIdentity" :identity="props.registration.memberIdentity"/>
      <MutedText size="sm">{{ formatDate(props.registration.registration.eventDate) }}</MutedText>
      <RegistrationStatusBadge
          :status="props.registration.registration.status"
          :queue-position="props.registration.registration.status === RegistrationStatus.QUEUED
            ? partnerQueuePosition(props.queue ?? [], props.registration.registration.id) : null"/>
      <ErrorBadge v-if="props.registration.registration.agreementWithdrawnAt" data-testid="federated-agreement-withdrawn">
        {{ t('eventDetail.federatedAgreementWithdrawn') }}
      </ErrorBadge>
    </div>
    <RegistrationFieldAnswers
        :fields="props.fields"
        :values="props.answers"
        :data-testid="`federated-answers-${props.registration.registration.id}`"
    />
  </div>
</template>
