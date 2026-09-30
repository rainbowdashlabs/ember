/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DiscoveryInstanceNote from '@/components/discovery/DiscoveryInstanceNote.vue'

/**
 * A sample station card as the directory shows it. `host` marks it as a station of another
 * instance; the slot holds what the card offers at its foot.
 */
defineProps<{
  name: string
  text?: string
  host?: string
}>()
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
    <MutedText v-if="text" size="sm">{{ text }}</MutedText>
    <div class="flex items-center gap-2 flex-wrap mt-auto pt-2">
      <slot/>
    </div>
  </NeutralContainer>
</template>
