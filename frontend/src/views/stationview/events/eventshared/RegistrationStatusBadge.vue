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
import {RegistrationStatus} from '@/api/generated/schema'

/**
 * What somebody answered, in one badge.
 *
 * <p>The same four words wherever an answer is shown: the appointment list, its detail page and the
 * dashboard. Written once so a place that shows the answer cannot invent a fifth word for it.
 */
const props = defineProps<{
  status: string
}>()

const {t} = useI18n()
const label = computed(() => registrationStatusLabel(props.status, t))
</script>

<template>
  <SuccessBadge v-if="status === RegistrationStatus.ACCEPTED" data-testid="registration-status">{{ label }}</SuccessBadge>
  <InfoBadge v-else-if="status === RegistrationStatus.PENDING" data-testid="registration-status">{{ label }}</InfoBadge>
  <ErrorBadge v-else data-testid="registration-status">{{ label }}</ErrorBadge>
</template>
