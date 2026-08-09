/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Help article for the member-check overview. The app serves the very same view under
 * {@code inventory/checks} and {@code inventory/checks/member}, so this renders
 * {@link CheckOverviewHelp} — a separate component file only so both routes own a help
 * entry instead of sharing one and tripping the duplicate-component check.
 */
import CheckOverviewHelp from './CheckOverviewHelp.vue'
</script>

<template>
  <CheckOverviewHelp/>
</template>
