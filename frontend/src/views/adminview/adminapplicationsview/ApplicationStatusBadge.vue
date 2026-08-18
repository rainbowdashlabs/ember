/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {ApplicationStatus, type ApplicationStatusName} from '@/api/stationApplications'

const {t} = useI18n()

defineProps<{
  status: ApplicationStatusName
}>()
</script>

<template>
  <SuccessBadge v-if="status === ApplicationStatus.ACCEPTED">{{ t('adminApplications.accepted') }}</SuccessBadge>
  <ErrorBadge v-else-if="status === ApplicationStatus.DENIED">{{ t('adminApplications.denied') }}</ErrorBadge>
  <SecondaryBadge v-else>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
</template>
