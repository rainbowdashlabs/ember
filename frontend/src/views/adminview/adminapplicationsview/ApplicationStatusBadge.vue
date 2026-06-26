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

const {t} = useI18n()

defineProps<{
  status: string
}>()
</script>

<template>
  <SuccessBadge v-if="status === 'accepted'">{{ t('adminApplications.accepted') }}</SuccessBadge>
  <ErrorBadge v-else-if="status === 'denied'">{{ t('adminApplications.denied') }}</ErrorBadge>
  <SecondaryBadge v-else>{{ t('adminApplications.pendingBadge') }}</SecondaryBadge>
</template>
