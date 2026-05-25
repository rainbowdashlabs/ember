/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type {DiscoveryEntry} from '@/api/discovery'

const {t} = useI18n()

defineProps<{
  stations: DiscoveryEntry[]
  canConnect: boolean
  showInvite: boolean
}>()

const emit = defineEmits<{
  connect: [station: DiscoveryEntry]
  invite: [station: DiscoveryEntry]
}>()

function logoUrl(station: DiscoveryEntry): string {
  return `/api/v1/public/stations/${station.stationUid}/logo`
}

function kbUrl(station: DiscoveryEntry): string {
  return `/public/kb/${station.stationUid}`
}
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
    <NeutralContainer v-for="station in stations" :key="station.stationUid" class="flex flex-col gap-2">
      <div class="flex items-center gap-3">
        <img v-if="station.hasLogo" :src="logoUrl(station)" :alt="station.name" class="w-10 h-10 rounded-full object-cover"/>
        <div v-else class="w-10 h-10 rounded-full bg-[var(--bg-accent)] flex items-center justify-center">
          <font-awesome-icon :icon="['fas', 'building']" class="text-[var(--text-muted)]"/>
        </div>
        <div class="min-w-0 flex-1">
          <div class="font-medium truncate">{{ station.name }}</div>
        </div>
      </div>

      <MutedText v-if="station.description" size="sm">{{ station.description }}</MutedText>

      <div class="flex items-center gap-2 flex-wrap mt-auto pt-2">
        <SuccessBadge v-if="station.alreadyFederated">{{ t('discovery.alreadyConnected') }}</SuccessBadge>
        <PrimaryButton v-else-if="canConnect" compact @click="emit('connect', station)">
          <font-awesome-icon :icon="['fas', 'handshake']" class="mr-1"/>
          {{ t('discovery.connect') }}
        </PrimaryButton>
        <SecondaryButton v-if="showInvite && !station.alreadyFederated" compact @click="emit('invite', station)">
          <font-awesome-icon :icon="['fas', 'link']" class="mr-1"/>
          {{ t('discovery.getCode') }}
        </SecondaryButton>
        <router-link v-if="station.hasPublicKb" :to="kbUrl(station)" class="text-sm text-[var(--link)] hover:underline flex items-center gap-1">
          <font-awesome-icon :icon="['fas', 'book']"/>
          {{ t('discovery.viewKb') }}
        </router-link>
      </div>
    </NeutralContainer>
  </div>
</template>
