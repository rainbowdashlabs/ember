/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * A button that brings no look of its own.
 *
 * <p>For the controls whose look belongs to what they sit in rather than to a button family: a row
 * of a list to pick from, a tile, a tab, a switcher in the header, a floating action. The caller
 * styles it with classes as it would the element, and everything else it is given reaches the
 * element too. It is a button of type `button` unless the caller says otherwise.
 */
</script>

<template>
  <button type="button">
    <slot/>
  </button>
</template>
