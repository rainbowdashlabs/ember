/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import DiscoveryInstanceNote from '@/components/discovery/DiscoveryInstanceNote.vue'

/**
 * A sample station tile as the directory shows it. `host` marks it as a station of another instance,
 * `offers` names the public parts it shows as chips; the slot holds what the tile offers at its foot.
 */
defineProps<{
  name: string
  text?: string
  host?: string
  place?: string
  offers?: {key: string; icon: string[]}[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="flex flex-col gap-2">
    <div class="flex items-center gap-3">
      <div class="w-10 h-10 rounded-full bg-[var(--bg-accent)] flex items-center justify-center">
        <font-awesome-icon :icon="['fas', 'building']" class="text-[var(--text-muted)]"/>
      </div>
      <div class="min-w-0 flex-1">
        <div class="font-medium truncate">{{ name }}</div>
        <DiscoveryInstanceNote v-if="host" :host="host"/>
      </div>
    </div>
    <MutedText v-if="text" tag="p" size="sm">{{ text }}</MutedText>
    <MutedText v-if="place" tag="p" size="sm" class="flex items-center gap-1.5">
      <AppIcon :icon="['fas', 'location-dot']" aria-hidden="true"/>
      {{ place }}
    </MutedText>
    <ul v-if="offers" class="flex flex-wrap gap-1.5">
      <li v-for="offer in offers" :key="offer.key">
        <SecondaryBadge>
          <AppIcon :icon="offer.icon" aria-hidden="true" class="mr-1"/>
          {{ t(`discovery.offer.${offer.key}`) }}
        </SecondaryBadge>
      </li>
    </ul>
    <div class="flex items-center gap-2 flex-wrap mt-auto pt-2">
      <slot/>
    </div>
  </NeutralContainer>
</template>
