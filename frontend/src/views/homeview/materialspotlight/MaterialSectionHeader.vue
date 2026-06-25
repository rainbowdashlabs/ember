/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
defineProps<{
  icon: string[]
  iconWarn?: boolean
  title: string
  count: number
}>()
</script>

<template>
  <div class="sub">
    <font-awesome-icon :icon="icon" class="sub-icon" :class="{'sub-icon--warn': iconWarn}"/>
    <span class="sub-title">{{ title }}</span>
    <span class="sub-count">({{ count }})</span>
  </div>
</template>

<style scoped>
.sub {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  padding: 1rem 1.25rem 0.5rem;
}
.sub-icon {
  width: 0.8rem;
  height: 0.8rem;
  opacity: 0.6;
}
.sub-icon--warn { color: var(--color-error); opacity: 0.85; }
.sub-title {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.85rem;
  font-weight: 700;
}
.sub-count {
  font-family: 'JetBrains Mono', ui-monospace, monospace;
  font-size: 0.72rem;
  opacity: 0.5;
}
</style>
