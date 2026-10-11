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
 *
 * <p>Paragraphs stand as close as in the text editor and on pages: the typography plugin's own spacing
 * left a gap between two paragraphs that read like an empty line.
 */
</script>

<template>
  <div class="prose prose-sm dark:prose-invert max-w-none leading-[1.6] prose-p:my-2 prose-p:leading-[1.6]">
    <slot/>
  </div>
</template>
