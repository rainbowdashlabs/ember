/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
defineProps<{
  open: boolean
  stationName?: string
  stationLogoUrl?: string | null
}>()

defineEmits<{
  close: []
}>()
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
      class="fixed top-0 left-0 z-40 h-full w-64 shrink-0 bg-bg-light-accent dark:bg-bg-dark-accent border-r border-bg-light-accent dark:border-bg-dark transition-transform duration-200 lg:translate-x-0 lg:static lg:z-auto lg:h-auto lg:min-h-screen"
  >
    <div class="flex items-center gap-3 h-14 px-4 border-b border-bg-light dark:border-bg-dark">
      <img v-if="stationLogoUrl" :src="stationLogoUrl" alt="" class="h-8 w-8 rounded object-contain shrink-0"/>
      <div class="flex flex-col justify-center">
        <span class="text-lg font-bold text-primary leading-tight">Ember</span>
        <span v-if="stationName" class="text-xs text-(--text-muted) leading-tight">{{ stationName }}</span>
      </div>
    </div>

    <nav class="flex flex-col gap-1 p-3">
      <slot/>
    </nav>
  </aside>
</template>
