/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import type {PoolStanding} from '@/api/generated/schema'

/**
 * How the stations' share of one of the instance's providers stands today: what all stations together
 * sent through it against what they may, and on the instance's own overview which stations that was.
 */
const props = defineProps<{
  pool: PoolStanding
}>()

const {t} = useI18n()

const stations = computed(() => props.pool.stations
    .map(station => t('instanceMail.pool.station', {name: station.name, count: station.sentToday}))
    .join(', '))
</script>

<template>
  <div class="space-y-1" data-testid="mail-pool-standing">
    <MutedText tag="p" size="sm">
      {{ pool.limit === null
        ? t('instanceMail.pool.sentNoLimit', {sent: pool.sentToday})
        : t('instanceMail.pool.sentOfLimit', {sent: pool.sentToday, limit: pool.limit}) }}
    </MutedText>
    <MutedText v-if="pool.stations.length > 0" tag="p" size="sm">
      {{ t('instanceMail.pool.stations', {stations}) }}
    </MutedText>
  </div>
</template>
