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
import ErrorButton from '@/components/button/ErrorButton.vue'
import type {FederatedEventRegistration} from '@/api/events'

defineProps<{
  registrations: FederatedEventRegistration[]
}>()

const emit = defineEmits<{
  accept: [registrationId: number]
  deny: [registrationId: number]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="registrations.length > 0" class="space-y-3">
    <SubHeader>{{ t('eventDetail.federatedRegistrations') }}</SubHeader>
    <div class="space-y-2">
      <NeutralContainer v-for="fr in registrations" :key="fr.registration.id" class="flex items-center justify-between gap-2">
        <div class="flex items-center gap-2">
          <MemberName v-if="fr.memberIdentity" :identity="fr.memberIdentity"/>
          <MutedText size="sm">{{ fr.registration.eventDate }}</MutedText>
          <SuccessBadge v-if="fr.registration.status === 'ACCEPTED'">{{ t('eventsUpcoming.statusAccepted') }}</SuccessBadge>
          <InfoBadge v-else-if="fr.registration.status === 'PENDING'">{{ t('eventsUpcoming.statusPending') }}</InfoBadge>
          <ErrorBadge v-else-if="fr.registration.status === 'DENIED'">{{ t('eventsUpcoming.statusDenied') }}</ErrorBadge>
        </div>
        <div v-if="fr.registration.status === 'PENDING'" class="flex items-center gap-2">
          <PrimaryButton compact @click="emit('accept', fr.registration.id)">
            <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>
            {{ t('eventsRegistrations.accept') }}
          </PrimaryButton>
          <ErrorButton compact @click="emit('deny', fr.registration.id)">
            <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1"/>
            {{ t('eventsRegistrations.deny') }}
          </ErrorButton>
        </div>
      </NeutralContainer>
    </div>
  </NeutralContainer>
</template>
