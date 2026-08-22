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
 */
const {t} = useI18n()
const {homeStationId} = useClusterHomeStation()
</script>

<template>
  <div class="space-y-6">
    <InventoryTabs/>
    <ManageView v-if="homeStationId"
                :subtitle="t('pages.cluster-inventory.subtitle')"
                :title="t('pages.cluster-inventory.title')"/>
  </div>
</template>
