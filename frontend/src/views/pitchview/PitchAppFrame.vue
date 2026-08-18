/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {emberLogo} from '@/composables/useEmberLogo'

/**
 * The frame a preview sits in: the header the application carries above every page, with the
 * station on the left and page title and subtitle beside it. The module navigation is left out -
 * on a slide it costs width without saying anything.
 */
defineProps<{
  station: string
  title: string
  subtitle?: string
}>()

const logo = emberLogo()
</script>

<template>
  <div class="overflow-hidden rounded-theme border border-bg-light-accent bg-(--bg) dark:border-bg-dark-accent">
    <div class="flex min-h-11 items-center gap-3 border-b border-bg-light-accent px-3 py-1.5
                dark:border-bg-dark-accent">
      <LayeredEmberLogo :layers="logo.layers" :active-layers="logo.activeLayers" :pixel-size="128"
                        size="h-6 w-6 shrink-0"/>
      <p class="hidden shrink-0 text-xs text-(--text-muted) sm:block">{{ station }}</p>
      <div class="min-w-0 border-l border-bg-light-accent pl-3 dark:border-bg-dark-accent">
        <p class="truncate text-sm font-semibold">{{ title }}</p>
        <p v-if="subtitle" class="truncate text-[0.65rem] text-(--text-muted)">{{ subtitle }}</p>
      </div>
    </div>
    <div class="space-y-2 p-3">
      <slot/>
    </div>
  </div>
</template>
