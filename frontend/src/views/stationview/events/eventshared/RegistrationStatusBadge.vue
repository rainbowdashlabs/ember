/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {registrationStatusLabel} from './registrationStatusLabel'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {RegistrationStatus} from '@/api/generated/schema'

/**
 * What somebody answered, in one badge.
 *
 * <p>The same words wherever an answer is shown: the appointment list, its detail page and the
 * dashboard. Written once so a place that shows the answer cannot invent another word for it. A
 * sign-up on the waiting list shows its place in line, and once the date is over that it did not get
 * a place.
 */
const props = defineProps<{
  status: string
  /** The place in line on the waiting list, where the sign-up waits on it. */
  queuePosition?: number | null
  /** Whether the date is already over. */
  past?: boolean
}>()

const {t} = useI18n()
const label = computed(() =>
    registrationStatusLabel(props.status, t, {queuePosition: props.queuePosition, past: props.past}))
const waiting = computed(() => props.status === RegistrationStatus.QUEUED && !props.past)
</script>

<template>
  <SuccessBadge v-if="status === RegistrationStatus.ACCEPTED" data-testid="registration-status">{{ label }}</SuccessBadge>
  <InfoBadge v-else-if="status === RegistrationStatus.PENDING" data-testid="registration-status">{{ label }}</InfoBadge>
  <SecondaryBadge v-else-if="waiting" data-testid="registration-status">{{ label }}</SecondaryBadge>
  <ErrorBadge v-else data-testid="registration-status">{{ label }}</ErrorBadge>
</template>
