/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import InvitationAppointment from './InvitationAppointment.vue'
import type {WaitingListPublicInvitation} from '@/api/generated/schema'

/**
 * The dates somebody was invited to, said exactly as the mail said them.
 *
 * The page is the invitation rather than a window into the station, and an answer given without
 * knowing the occasion is not an answer worth collecting. "None of these suit" in particular is
 * about dates that have to be on the screen.
 */
const props = defineProps<{invitation: WaitingListPublicInvitation}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="waitlist-invitation">
    <SectionHeader class="text-lg font-semibold">{{ t('waitingList.publicStatus.invitationTitle') }}</SectionHeader>
    <p class="text-sm">
      {{ props.invitation.appointments.length > 1
        ? t('waitingList.publicStatus.invitationIntroSeveral')
        : t('waitingList.publicStatus.invitationIntro') }}
    </p>
    <blockquote
        v-if="props.invitation.message"
        class="border-l-4 border-primary pl-3 text-sm whitespace-pre-line"
        data-testid="waitlist-invitation-message"
    >{{ props.invitation.message }}</blockquote>
    <template v-if="props.invitation.appointments.length > 1">
      <div v-for="(appointment, index) in props.invitation.appointments" :key="appointment.position" class="space-y-2">
        <p class="text-sm font-semibold">{{ t('waitingList.publicStatus.invitationOption', {number: index + 1}) }}</p>
        <InvitationAppointment :appointment="appointment" />
      </div>
    </template>
    <InvitationAppointment
        v-else-if="props.invitation.appointments.length === 1"
        :appointment="props.invitation.appointments[0]!"
    />
  </NeutralContainer>
</template>
