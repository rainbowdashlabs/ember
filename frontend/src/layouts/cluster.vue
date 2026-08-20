/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ClusterView from '~/views/ClusterView.vue'
import {usePageTitle} from '~/composables/usePageTitle'

usePageTitle()
</script>

<template>
  <ClusterView>
    <slot />
  </ClusterView>
</template>
