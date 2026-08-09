/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseButton from './BaseButton.vue'
</script>

<template>
  <BaseButton class="bg-info text-info-text hover:brightness-110">
    <slot/>
  </BaseButton>
</template>
