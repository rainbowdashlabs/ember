/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
</script>

<template>
  <div class="space-y-3">
    <SubHeader><slot name="title"/></SubHeader>
    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <slot/>
    </div>
  </div>
</template>
