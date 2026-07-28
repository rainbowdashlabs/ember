/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type {SpecialMention} from '@/composables/useMentionQuery'

defineProps<{
  data: SpecialMention
}>()
</script>

<template>
  <div class="h-6 w-6 shrink-0 rounded-full bg-primary/15 text-primary flex items-center justify-center">
    <font-awesome-icon :icon="data.icon" class="h-3 w-3" />
  </div>
  <span class="font-medium truncate">{{ data.label }}</span>
</template>
