/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import HelpCenterAdminView from '~/views/HelpCenterAdminView.vue'
import {usePageTitle} from '~/composables/usePageTitle'
import {loadHelpcenterMessages} from '~/composables/useHelpcenterMessages'
import {hideMemberCards} from '~/composables/useMemberCards'

hideMemberCards()
await loadHelpcenterMessages()
usePageTitle()
</script>

<template>
  <HelpCenterAdminView>
    <slot />
  </HelpCenterAdminView>
</template>
