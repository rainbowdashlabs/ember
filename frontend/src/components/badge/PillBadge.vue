/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * The pill shape of a badge, with the colour and the size left to the caller.
 *
 * <p>For the small labels whose colour says something only their own screen knows, such as a status,
 * a strategy or a chosen value with a way to take it off again. The named badges carry a colour of
 * the theme; this one carries the shape and nothing else.
 */
</script>

<template>
  <span class="inline-flex items-center rounded-full">
    <slot/>
  </span>
</template>
