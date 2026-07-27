/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import BaseButton from '@/components/button/BaseButton.vue'
import type { TestProtocolItem } from '@/api/protocol'

defineProps<{
  item: TestProtocolItem
  checked: boolean
}>()

defineEmits<{
  (e: 'toggle'): void
}>()
</script>

<template>
  <BaseButton
    full-width
    class="!flex items-center gap-2 !p-3 !rounded-lg border-2 !font-normal text-left cursor-pointer"
    :class="checked
      ? 'border-[var(--success)] bg-[color-mix(in_srgb,var(--success)_10%,transparent)]'
      : 'border-[var(--border)] hover:border-[var(--text-muted)]'"
    @click="$emit('toggle')"
  >
    <font-awesome-icon
      :icon="['fas', checked ? 'square-check' : 'square']"
      :class="['w-6 h-6 transition-colors', checked ? 'text-[var(--success)]' : 'text-[var(--text-muted)]']"
    />
    <span class="flex-1 text-sm">{{ item.label }}</span>
    <span class="text-xs text-[var(--text-muted)] font-mono">{{ item.points }}P</span>
  </BaseButton>
</template>
