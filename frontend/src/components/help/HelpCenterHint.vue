/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {RouterLink} from 'vue-router'
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import {emberLogoFaq, defaultGazePositions} from '@/composables/useEmberLogo'

/**
 * Small inline hint that nudges the user to the matching help-center article. Uses the
 * animated ember logo's FAQ variant (mascot + question mark + auto-blink + gaze) so the
 * affordance is recognisable across the app without needing extra explainer copy.
 *
 * @example
 *   <HelpCenterHint to="help-station-feed">Mehr Infos zum Feed →</HelpCenterHint>
 */
const props = withDefaults(defineProps<{
  /** Named route or path to the matching help-center page. */
  to: string | { name: string }
}>(), {
})

const logo = emberLogoFaq()
</script>

<template>
  <RouterLink :to="props.to"
              class="inline-flex items-center gap-2 group text-sm text-(--text-muted) hover:text-(--text) transition-colors">
    <LayeredEmberLogo
        :layers="logo.layers"
        :active-layers="logo.activeLayers"
        size="w-8 h-8"
        :pixel-size="128"
        :auto-blink="true"
        :gaze-positions="defaultGazePositions"
        :bounce="true"
    />
    <span class="underline underline-offset-2 group-hover:no-underline">
      <slot />
    </span>
  </RouterLink>
</template>
