/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseBadge from './BaseBadge.vue'
</script>

<template>
  <BaseBadge class="bg-error/15 text-error-badge">
    <slot/>
  </BaseBadge>
</template>
