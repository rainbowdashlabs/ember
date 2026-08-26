/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MovementFlowPanel from './manageview/MovementFlowPanel.vue'

/**
 * Where a station writes the chains its gear walks.
 *
 * <p>A page of its own, reached from the sidebar. These lived at the bottom of the inventory list,
 * under the inventories and the loss settings, which is not where anybody looks for them: the chains
 * decide what happens to every piece the station moves, and that is not a footnote to a list.
 */
const {t} = useI18n()
</script>

<template>
  <ViewContent :subtitle="t('flows.intro')" :title="t('flows.title')">
    <MovementFlowPanel/>
  </ViewContent>
</template>
