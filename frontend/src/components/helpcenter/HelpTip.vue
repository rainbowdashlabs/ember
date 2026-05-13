/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import PrimaryContainer from '@/components/container/PrimaryContainer.vue'
</script>

<template>
  <PrimaryContainer class="text-sm flex items-start gap-2">
    <font-awesome-icon :icon="['fas', 'circle-info']" class="w-4 h-4 mt-0.5 shrink-0"/>
    <div>
      <slot/>
    </div>
  </PrimaryContainer>
</template>
