/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * One input with its label above it and, where there is one, a line underneath saying what it is for.
 *
 * <p>A form of eight fields is eight of these rather than eight hand-built wrappers, which is what keeps
 * the labels, the spacing and the hint text the same everywhere without anybody remembering to make them
 * so.
 */
defineProps<{
  label: string
  /** Whether the field may be left as it is, which the label marks. */
  hint?: boolean
  /** A line under the field, for the ones whose consequence is not obvious from the label. */
  help?: string
  /** What is wrong with what the field holds, shown under it in place of the help. */
  error?: string
}>()
</script>

<template>
  <div class="space-y-1">
    <FieldLabel :hint="hint">{{ label }}</FieldLabel>
    <slot/>
    <p v-if="error" class="text-sm text-error" role="alert">{{ error }}</p>
    <MutedText v-else-if="help" size="sm" tag="p">{{ help }}</MutedText>
  </div>
</template>
