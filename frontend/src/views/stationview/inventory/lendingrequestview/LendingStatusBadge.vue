/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {LendingStatus} from '@/api/generated/schema'

/** Where a lending request stands, coloured by whether it goes ahead, and overdue where it is. */
defineProps<{
  status: LendingStatus
  overdue?: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <span class="inline-flex flex-wrap gap-1">
    <SuccessBadge v-if="status === LendingStatus.APPROVED || status === LendingStatus.LENT">{{ t(`lending.status.${status}`) }}</SuccessBadge>
    <ErrorBadge v-else-if="status === LendingStatus.DECLINED">{{ t(`lending.status.${status}`) }}</ErrorBadge>
    <InfoBadge v-else-if="status === LendingStatus.CLOSED">{{ t(`lending.status.${status}`) }}</InfoBadge>
    <SecondaryBadge v-else>{{ t(`lending.status.${status}`) }}</SecondaryBadge>
    <ErrorBadge v-if="overdue">{{ t('lending.overdue') }}</ErrorBadge>
  </span>
</template>
