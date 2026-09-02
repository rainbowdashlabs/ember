/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DummyInventoryCard from '@/views/helpcenter/stationview/inventory/managehelp/DummyInventoryCard.vue'

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('inventory.manage.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']">
        {{ t('inventory.manage.create') }}
      </PrimaryButton>
    </div>

    <DummyInventoryCard name="Helme" :type="t('inventory.manage.type.INTERNAL')"
                        :share-state="t('lendingShare.stateAllPartners')" with-sizes>
      <template #sizesLabel>{{ t('inventory.manage.withSizes') }}</template>
      <template #meta>
        {{ t('inventory.manage.itemCount', { count: 24 }) }}
        &middot; <span class="text-error">{{ t('inventory.manage.lostCount', { count: 1 }) }}</span>
        &middot; <span class="text-secondary-accent dark:text-secondary">{{ t('inventory.manage.lentOutCount', { count: 2 }) }}</span>
      </template>
    </DummyInventoryCard>

    <DummyInventoryCard name="Jacken" :type="t('inventory.manage.type.EXTERNAL')">
      <template #meta>
        {{ t('inventory.manage.itemCount', { count: 18 }) }}
        &middot; <span class="text-info-accent dark:text-info">{{ t('inventory.manage.procurementCount', { count: 2 }) }}</span>
      </template>
    </DummyInventoryCard>

    <DummyInventoryCard name="Stiefel" :type="t('inventory.manage.type.MIXED')"
                        :share-state="t('lendingShare.stateUnshared')" with-sizes>
      <template #sizesLabel>{{ t('inventory.manage.withSizes') }}</template>
      <template #meta>
        {{ t('inventory.manage.itemCount', { count: 30 }) }}
      </template>
    </DummyInventoryCard>
  </div>
</template>
