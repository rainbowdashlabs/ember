/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import StatTile from '@/components/statistic/StatTile.vue'

const props = defineProps<{
  totalStations: number
  totalAccounts: number
  totalMembers: number
  activeSessions: number
  totalGroups: number
  twoFactorAccounts: number
}>()

const {t} = useI18n()

const tiles = computed(() => [
  {key: 'stations', value: props.totalStations},
  {key: 'accounts', value: props.totalAccounts},
  {key: 'members', value: props.totalMembers},
  {key: 'activeSessions', value: props.activeSessions},
  {key: 'groups', value: props.totalGroups},
  {key: 'twoFactorAccounts', value: props.twoFactorAccounts},
])
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('adminStats.platformSection') }}</SubHeader>
    <div class="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-4">
      <StatTile v-for="tile in tiles" :key="tile.key" :label="t(`adminStats.${tile.key}`)" :value="tile.value"/>
    </div>
  </div>
</template>
