/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'
</script>

<template>
  <BaseBadge class="bg-primary/15 text-primary-accent dark:text-primary">
    <slot/>
  </BaseBadge>
</template>
