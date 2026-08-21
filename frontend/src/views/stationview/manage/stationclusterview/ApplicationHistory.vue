/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {StationClusterApplication} from '@/api/clusterStations'
import {formatDate} from '@/util/format'

defineProps<{
  applications: StationClusterApplication[]
}>()

const {t} = useI18n()
</script>

<template>
  <div v-if="applications.length > 0" class="space-y-2">
    <SectionHeader>{{ t('stationCluster.historyTitle') }}</SectionHeader>
    <NeutralContainer
        v-for="application in applications"
        :key="application.id"
        class="flex flex-wrap items-center justify-between gap-3"
    >
      <div>
        <p class="font-medium">{{ application.clusterName }}</p>
        <p class="text-sm text-(--text-muted)">
          {{ formatDate(application.requestedAt) }}
          <template v-if="application.denyReason"> - {{ application.denyReason }}</template>
        </p>
      </div>
      <SecondaryBadge>{{ t(`clusterApplications.status.${application.status}`) }}</SecondaryBadge>
    </NeutralContainer>
  </div>
</template>
