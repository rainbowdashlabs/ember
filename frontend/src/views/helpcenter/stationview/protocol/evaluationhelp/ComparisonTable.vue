/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ComparisonTableHead from './ComparisonTableHead.vue'
import ComparisonTableBody from './ComparisonTableBody.vue'
</script>

<template>
  <div class="overflow-x-auto">
    <table class="text-xs w-full" style="border-collapse: separate; border-spacing: 0">
      <ComparisonTableHead />
      <ComparisonTableBody />
    </table>
  </div>
</template>
