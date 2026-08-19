/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import StatValue from '@/components/typography/StatValue.vue'

/**
 * One figure with its caption, the unit every statistics grid is built from.
 *
 * `highlight` colours the figure only while it is above zero, which is what a count of things
 * needing attention wants: loud when there is something, quiet when there is not.
 */
withDefaults(defineProps<{
  value: number | string
  label: string
  color?: 'primary' | 'success' | 'error' | 'muted' | 'info'
  highlight?: 'error' | 'info'
}>(), {
  color: 'primary',
  highlight: undefined,
})
</script>

<template>
  <NeutralContainer class="text-center">
    <StatValue :color="highlight ? (Number(value) > 0 ? highlight : 'muted') : color">{{ value }}</StatValue>
    <p class="text-sm text-(--text-muted)">{{ label }}</p>
  </NeutralContainer>
</template>
