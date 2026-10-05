/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import BaseButton from '@/components/button/BaseButton.vue'
import ProtocolItemPoints from '@/components/protocol/ProtocolItemPoints.vue'
import type { TestProtocolItem } from '@/api/generated/schema'

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
    class="!flex items-center gap-2 !p-3 !rounded-lg border-2 !font-normal !whitespace-normal text-left cursor-pointer"
    :class="checked
      ? 'border-[var(--color-success)] bg-[color-mix(in_srgb,var(--color-success)_10%,transparent)]'
      : 'border-[var(--border)] hover:border-[var(--text-muted)]'"
    @click="$emit('toggle')"
  >
    <font-awesome-icon
      :icon="['fas', checked ? 'square-check' : 'square']"
      :class="['w-6 h-6 shrink-0 transition-colors', checked ? 'text-[var(--color-success)]' : 'text-[var(--text-muted)]']"
    />
    <span class="flex-1 min-w-0 break-words text-sm">{{ item.label }}</span>
    <ProtocolItemPoints :item="item" />
  </BaseButton>
</template>
