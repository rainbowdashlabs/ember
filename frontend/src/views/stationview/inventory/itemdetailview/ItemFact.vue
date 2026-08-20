/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import FieldLabel from '@/components/typography/FieldLabel.vue'

defineProps<{
  label: string
}>()
</script>

<template>
  <div>
    <FieldLabel class="text-xs">{{ label }}</FieldLabel>
    <slot/>
  </div>
</template>
