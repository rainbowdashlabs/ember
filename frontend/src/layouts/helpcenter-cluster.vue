/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import HelpCenterClusterView from '~/views/HelpCenterClusterView.vue'
import {usePageTitle} from '~/composables/usePageTitle'
import {loadHelpcenterMessages} from '~/composables/useHelpcenterMessages'

await loadHelpcenterMessages()
usePageTitle()
</script>

<template>
  <HelpCenterClusterView>
    <slot />
  </HelpCenterClusterView>
</template>
