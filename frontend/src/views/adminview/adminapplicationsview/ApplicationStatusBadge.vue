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
import {ApplicationStatus} from '@/api/generated/schema'

const {t} = useI18n()

defineProps<{
  status: ApplicationStatus
}>()
</script>

<template>
  <SuccessBadge v-if="status === ApplicationStatus.ACCEPTED">{{ t('adminApplications.accepted') }}</SuccessBadge>
  <ErrorBadge v-else-if="status === ApplicationStatus.DENIED">{{ t('adminApplications.denied') }}</ErrorBadge>
  <SecondaryBadge v-else-if="status === ApplicationStatus.PENDING">{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
  <SecondaryBadge v-else>{{ t('adminApplications.unverified') }}</SecondaryBadge>
</template>
