/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StatusFieldRow from './StatusFieldRow.vue'
import type {WaitingListPublicInvitation} from '@/api/waitingList'

/**
 * The evening somebody was invited to, said exactly as the mail said it.
 *
 * The page is the invitation rather than a window into the station, and an answer given without
 * knowing the occasion is not an answer worth collecting. "The date does not suit" in particular is
 * about a date that has to be on the screen.
 */
const props = defineProps<{invitation: WaitingListPublicInvitation}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="waitlist-invitation">
    <SectionHeader class="text-lg font-semibold">{{ t('waitingList.publicStatus.invitationTitle') }}</SectionHeader>
    <p class="text-sm">{{ t('waitingList.publicStatus.invitationIntro') }}</p>
    <div class="grid gap-2 sm:grid-cols-2">
      <StatusFieldRow
          v-if="props.invitation.appointmentName"
          wide
          :label="t('waitingList.publicStatus.invitationAppointment')"
          :value="props.invitation.appointmentName"
      />
      <StatusFieldRow
          v-if="props.invitation.appointmentDate"
          :label="t('waitingList.publicStatus.invitationDate')"
          :value="props.invitation.appointmentDate"
      />
      <StatusFieldRow
          v-if="props.invitation.appointmentTime"
          :label="t('waitingList.publicStatus.invitationTime')"
          :value="props.invitation.appointmentTime"
      />
      <StatusFieldRow
          v-if="props.invitation.arrivalTime"
          :label="t('waitingList.publicStatus.invitationArrival')"
          :value="props.invitation.arrivalTime"
      />
      <StatusFieldRow
          v-if="props.invitation.location"
          wide
          :label="t('waitingList.publicStatus.invitationLocation')"
          :value="props.invitation.location"
      />
    </div>
  </NeutralContainer>
</template>
