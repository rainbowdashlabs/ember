/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import HelpCenterStationView from '~/views/HelpCenterStationView.vue'
import {usePageTitle} from '~/composables/usePageTitle'
import {loadHelpcenterMessages} from '~/composables/useHelpcenterMessages'

await loadHelpcenterMessages()
usePageTitle()
</script>

<template>
  <HelpCenterStationView>
    <slot />
  </HelpCenterStationView>
</template>
