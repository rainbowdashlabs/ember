/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'
</script>

<template>
  <BaseBadge class="bg-secondary/15 text-secondary-accent dark:text-secondary">
    <slot/>
  </BaseBadge>
</template>
