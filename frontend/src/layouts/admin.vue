/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AdminView from '~/views/AdminView.vue'
import {usePageTitle} from '~/composables/usePageTitle'

usePageTitle()
</script>

<template>
  <AdminView>
    <slot />
  </AdminView>
</template>
