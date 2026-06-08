/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PrideText from '@/components/display/PrideText.vue'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {usePride} from '@/composables/usePride'
import { emberLogo } from '@/composables/useEmberLogo'

defineProps<{
  open: boolean
  stationName?: string
  stationLogoUrl?: string | null
}>()

defineEmits<{
  close: []
}>()

const {prideActive, prideVariant} = usePride()
const sidebarLogo = emberLogo()
</script>

<template>
  <!-- Backdrop for mobile -->
  <div
      v-if="open"
      class="fixed inset-0 z-30 bg-black/50 lg:hidden"
      @click="$emit('close')"
  />

  <!-- Sidebar -->
  <aside
      :class="open ? 'translate-x-0' : '-translate-x-full'"
      class="fixed top-0 left-0 z-40 h-full w-64 shrink-0 flex flex-col bg-bg-light-accent dark:bg-bg-dark-accent border-r border-bg-light-accent dark:border-bg-dark transition-transform duration-200 lg:translate-x-0 lg:static lg:z-auto lg:sticky lg:top-0 lg:h-screen"
  >
    <router-link to="/" class="flex items-center gap-3 h-14 px-4 border-b border-bg-light dark:border-bg-dark shrink-0 no-underline hover:bg-(--bg-accent) transition-colors">
      <LayeredEmberLogo v-if="!stationLogoUrl" :layers="sidebarLogo.layers" :active-layers="sidebarLogo.activeLayers" :auto-blink="true" size="h-8 w-8 shrink-0" :pixel-size="64" />
      <img v-else :src="stationLogoUrl" alt="" class="h-8 w-8 rounded object-contain shrink-0"/>
      <div class="flex flex-col justify-center">
        <PrideText :active="prideActive" :variant="prideVariant" class="text-lg font-bold text-primary leading-tight">Ember</PrideText>
        <span v-if="stationName" class="text-xs text-(--text-muted) leading-tight">{{ stationName }}</span>
      </div>
    </router-link>

    <nav class="flex flex-col gap-1 p-3 overflow-y-auto flex-1">
      <slot/>
    </nav>
  </aside>
</template>
