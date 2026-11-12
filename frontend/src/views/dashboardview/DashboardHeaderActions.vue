/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AccountMenuButton from '@/components/layout/AccountMenuButton.vue'
import MessagesButton from '@/components/layout/MessagesButton.vue'
import QuickSearchTrigger from '@/components/quicksearch/QuickSearchTrigger.vue'
import {useQuickSearch} from '@/composables/useQuickSearch'

/**
 * The station header: search, messages and the account. Switching places lives in the sidebar's
 * name, and help in the round buttons at the bottom right.
 */
const {open: openQuickSearch} = useQuickSearch()
</script>

<template>
  <QuickSearchTrigger scope="station" @open="openQuickSearch"/>
  <MessagesButton/>
  <AccountMenuButton/>
</template>
