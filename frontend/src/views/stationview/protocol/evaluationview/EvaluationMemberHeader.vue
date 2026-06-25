/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PrimaryButton from '@/components/button/PrimaryButton.vue'

defineProps<{
  name: string
}>()

defineEmits<{
  (e: 'openPdf'): void
}>()
</script>

<template>
  <th class="bg-[var(--bg-accent)] border border-[var(--border)] px-2 py-1.5 text-center min-w-20 whitespace-nowrap">
    <div class="font-medium mb-1">{{ name }}</div>
    <PrimaryButton class="!text-xs !py-0.5 !px-2" @click="$emit('openPdf')">
      <font-awesome-icon :icon="['fas', 'file-pdf']" class="mr-1" /> PDF
    </PrimaryButton>
  </th>
</template>
