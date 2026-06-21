/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AccountView from '~/views/AccountView.vue'
import {usePageTitle} from '~/composables/usePageTitle'

usePageTitle()
</script>

<template>
  <AccountView>
    <slot />
  </AccountView>
</template>
