/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Help article for creating a news entry. Writing and editing are the same screen, so this
 * renders {@link EditHelp} - a separate component file only so the create and the edit route
 * each own a help entry instead of sharing one and tripping the duplicate-component check.
 */
import EditHelp from './EditHelp.vue'
</script>

<template>
  <EditHelp/>
</template>
