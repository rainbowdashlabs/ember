/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Typography wrapper for rendered markdown / rich text. Owns the `prose` class stack so every
 * article, news entry and description body reads the same in light and dark mode. Accepts
 * `v-html` through attribute fallthrough for the server-rendered HTML call sites.
 */
</script>

<template>
  <div class="prose prose-sm dark:prose-invert max-w-none">
    <slot/>
  </div>
</template>
