/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PillBadge from '@/components/badge/PillBadge.vue'

/**
 * A size as a chip names it: small and quiet, unless it is the size that was asked for, which is what
 * a reader choosing a piece is looking for.
 */
const props = defineProps<{
  label: string
  /** Whether this is the size asked for, which draws it in the colour of a fit. */
  wanted?: boolean
}>()
</script>

<template>
  <PillBadge
      :class="props.wanted ? 'bg-success/20 font-semibold text-success' : 'bg-(--bg-accent) text-(--text-muted)'"
      class="shrink-0 gap-1 px-1.5 py-0.5 text-xs"
  >
    <font-awesome-icon v-if="props.wanted" :icon="['fas', 'check']"/>
    {{ props.label }}
  </PillBadge>
</template>
