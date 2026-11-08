/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * The row of round buttons at the bottom right of the page, laid out right to left in the order they
 * are given: bug report, help center, the onboarding to resume, developer tools. Each button only says
 * what it is; where it sits is this row's business, so two of them can never claim the same spot.
 */
</script>

<template>
  <div class="fixed bottom-4 right-4 z-40 flex flex-row-reverse items-center gap-2">
    <slot/>
  </div>
</template>
