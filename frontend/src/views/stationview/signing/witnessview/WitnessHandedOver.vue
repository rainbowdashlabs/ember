/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, onUnmounted} from 'vue'

/**
 * The screens the person who signs holds the device for, in the page like every other screen.
 *
 * <p>The sidebar, the header, the footer and the dock stay in sight but are made inert while it is shown:
 * neither clickable nor reachable by keyboard or screen reader, so the person reaches nothing of the
 * witness's own, only the steps of this signature. They come back once the witness has the device again.
 */
defineProps<{label: string}>()

/** What the application shell marks as its own controls around the page. */
const SHELL_CHROME = '[data-shell-chrome]'

function chrome(): Element[] {
  return Array.from(document.querySelectorAll(SHELL_CHROME))
}

onMounted(() => chrome().forEach(element => element.setAttribute('inert', '')))
onUnmounted(() => chrome().forEach(element => element.removeAttribute('inert')))
</script>

<template>
  <section class="space-y-4" :aria-label="label" data-testid="witness-handed-over-screen">
    <slot/>
  </section>
</template>
