/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  muted?: boolean
  align?: 'left' | 'center' | 'right'
}>()
</script>

<template>
  <td
      :class="[
        muted ? 'text-(--text-muted)' : '',
        align === 'center' ? 'text-center' : align === 'right' ? 'text-right' : '',
      ]"
      class="px-3 py-2"
  >
    <slot/>
  </td>
</template>
