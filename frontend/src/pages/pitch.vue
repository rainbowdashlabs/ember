/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useRoute} from 'vue-router'
import PitchDeckView from '~/views/PitchDeckView.vue'

/**
 * The positions sit on the route itself rather than in a `pages/pitch/` directory: a page nested
 * in a directory makes the router the component tests mount come up without an instance, and every
 * component test then fails during setup. Aliases are no way around it either — an alias may not
 * introduce a parameter the original record lacks.
 */
definePageMeta({
  path: '/pitch/:column?/:row?',
  layout: false,
  name: 'pitch',
  public: true,
  /**
   * A constant key keeps the deck mounted while the position in the address changes. Without it
   * every slide change re-creates the page, which shows as a flash between two slides.
   */
  key: 'pitch',
})

const route = useRoute()

/** One-based in the address: /pitch/3/2 is the third topic, its second slide. */
const column = computed(() => Number(route.params.column) || 1)
const row = computed(() => Number(route.params.row) || 1)

useHead({
  title: 'Pitch',
  meta: [{name: 'robots', content: 'noindex, nofollow'}],
})
</script>

<template>
  <PitchDeckView :column="column" :row="row"/>
</template>
