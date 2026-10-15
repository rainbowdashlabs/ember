/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, onUnmounted} from 'vue'

/**
 * The screens the person who signs holds the device for, laid over the whole application.
 *
 * <p>While it is shown, everything else on the page is made inert: the sidebar, the header, the search and
 * every link of the witness's own are neither clickable nor reachable by keyboard or screen reader. Only
 * the steps of this signature are. It is given back once the witness has the device again.
 */
defineProps<{label: string}>()

const APP_ROOT = '__nuxt'

onMounted(() => document.getElementById(APP_ROOT)?.setAttribute('inert', ''))
onUnmounted(() => document.getElementById(APP_ROOT)?.removeAttribute('inert'))
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 overflow-y-auto bg-(--bg) p-4 sm:p-8" role="dialog" aria-modal="true" :aria-label="label"
         data-testid="witness-handed-over-screen">
      <slot/>
    </div>
  </Teleport>
</template>
