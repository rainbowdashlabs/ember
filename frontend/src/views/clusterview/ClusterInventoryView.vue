/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ManageView from '@/views/stationview/inventory/ManageView.vue'
import InventoryTabs from './clusterinventoryview/InventoryTabs.vue'
import {useClusterHomeStation} from '@/composables/useClusterHomeStation'

/**
 * The association's own store.
 *
 * <p>The station's gear screen, shown at the station the association owns. Defining an inventory,
 * adding an item, giving it a size or a field of its own are the station's screens doing what they
 * already do, which is why none of it had to be written again.
 *
 * <p>The tabs go inside that screen rather than above it. Standing outside, they were the one thing on
 * this page with no margin around them, because the padding belongs to the screen they sat on top of.
 */
const {t} = useI18n()
const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <ManageView v-if="homeStationId"
              :subtitle="t('pages.cluster-inventory.subtitle')"
              :title="t('pages.cluster-inventory.title')">
    <template #before>
      <InventoryTabs/>
    </template>
  </ManageView>
</template>
