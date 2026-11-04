/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import StatusFieldRow from './StatusFieldRow.vue'
import type {WaitingListPublicAppointment} from '@/api/generated/schema'

/** One date an invitation offers, with whatever the mail said about it. */
const props = defineProps<{appointment: WaitingListPublicAppointment}>()

const {t} = useI18n()
</script>

<template>
  <div class="grid gap-2 sm:grid-cols-2" data-testid="waitlist-invitation-appointment">
    <StatusFieldRow
        v-if="props.appointment.appointmentName"
        wide
        :label="t('waitingList.publicStatus.invitationAppointment')"
        :value="props.appointment.appointmentName"
    />
    <StatusFieldRow
        v-if="props.appointment.appointmentDate"
        :label="t('waitingList.publicStatus.invitationDate')"
        :value="props.appointment.appointmentDate"
    />
    <StatusFieldRow
        v-if="props.appointment.appointmentTime"
        :label="t('waitingList.publicStatus.invitationTime')"
        :value="props.appointment.appointmentTime"
    />
    <StatusFieldRow
        v-if="props.appointment.arrivalTime"
        :label="t('waitingList.publicStatus.invitationArrival')"
        :value="props.appointment.arrivalTime"
    />
    <StatusFieldRow
        v-if="props.appointment.location"
        wide
        :label="t('waitingList.publicStatus.invitationLocation')"
        :value="props.appointment.location"
    />
  </div>
</template>
