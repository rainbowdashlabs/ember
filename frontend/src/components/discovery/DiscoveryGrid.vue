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
import ButtonRow from '@/components/button/ButtonRow.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import DiscoveryInstanceNote from '@/components/discovery/DiscoveryInstanceNote.vue'
import DiscoveryRemoteLink from '@/components/discovery/DiscoveryRemoteLink.vue'
import {isRemoteEntry} from '@/api/discovery'
import type {DiscoveryEntry} from '@/api/generated/schema'

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

/**
 * A key that stays unique across instances: two instances may well publish a station under the same
 * identifier, and neither of them is this instance's own.
 */
function cardKey(station: DiscoveryEntry): string {
  return `${station.instanceHost ?? ''}/${station.stationUid}`
}

/** Federation is asked for from this instance's stations only; a remote card offers its page instead. */
function offersFederation(station: DiscoveryEntry): boolean {
  return !station.isOwnStation && !station.alreadyFederated && !isRemoteEntry(station)
}
</script>

<template>
  <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
    <NeutralContainer v-for="station in stations" :key="cardKey(station)" class="flex flex-col gap-2">
      <div class="flex items-center gap-3">
        <img v-if="station.hasLogo && station.logoUrl" :src="station.logoUrl" :alt="station.name" class="w-10 h-10 rounded-full object-cover"/>
        <div v-else class="w-10 h-10 rounded-full bg-[var(--bg-accent)] flex items-center justify-center">
          <font-awesome-icon :icon="['fas', 'building']" class="text-[var(--text-muted)]"/>
        </div>
        <div class="min-w-0 flex-1">
          <div class="font-medium truncate">{{ station.name }}</div>
          <DiscoveryInstanceNote v-if="station.instanceHost" :host="station.instanceHost"/>
        </div>
      </div>

      <MutedText v-if="station.description" size="sm">{{ station.description }}</MutedText>

      <ButtonRow class="mt-auto pt-2">
        <PrimaryBadge v-if="station.isOwnStation">{{ t('discovery.ownStation') }}</PrimaryBadge>
        <SuccessBadge v-else-if="station.alreadyFederated">{{ t('discovery.alreadyConnected') }}</SuccessBadge>
        <PrimaryButton v-else-if="canConnect && offersFederation(station)" compact @click="emit('connect', station)">
          <font-awesome-icon :icon="['fas', 'handshake']" class="mr-1"/>
          {{ t('discovery.connect') }}
        </PrimaryButton>
        <SecondaryButton v-if="showInvite && offersFederation(station)" compact @click="emit('invite', station)">
          <font-awesome-icon :icon="['fas', 'link']" class="mr-1"/>
          {{ t('discovery.getCode') }}
        </SecondaryButton>
        <DiscoveryRemoteLink
            v-if="station.instanceHost && station.publicPageUrl"
            :host="station.instanceHost"
            :href="station.publicPageUrl"
            :name="station.name"
        />
        <router-link v-else-if="station.hasPublicKb || station.hasPublicCalendar"
                     :to="{name: 'public-station', params: {stationUid: station.publicSlug ?? station.stationUid}}"
                     class="text-sm text-[var(--link)] hover:underline flex items-center gap-1">
          <font-awesome-icon :icon="['fas', 'globe']"/>
          {{ t('discovery.viewStation') }}
        </router-link>
      </ButtonRow>
    </NeutralContainer>
  </div>
</template>
