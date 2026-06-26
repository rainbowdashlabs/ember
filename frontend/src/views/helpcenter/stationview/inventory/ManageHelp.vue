/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import DummyInventoryCards from '@/views/helpcenter/stationview/inventory/managehelp/DummyInventoryCards.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryManage.title')" :subtitle="t('helpCenter.inventoryManage.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryManage.whatIs')">
      <p>{{ t('helpCenter.inventoryManage.whatIsText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.INVENTORY_CREATE]" :label="t('helpCenter.permissionLabel.inventoryCreate')">
      <HelpSection :title="t('helpCenter.inventoryManage.createTitle')">
        <p>{{ t('helpCenter.inventoryManage.createName') }}</p>
        <p>{{ t('helpCenter.inventoryManage.createType') }}</p>
        <p>{{ t('helpCenter.inventoryManage.createSizes') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.inventoryManage.scanTitle')">
      <p>{{ t('helpCenter.inventoryManage.scanText') }}</p>
      <p>{{ t('helpCenter.scanShared.intro') }}</p>
      <ul class="list-disc pl-5 space-y-1">
        <li>{{ t('helpCenter.scanShared.tipPermission') }}</li>
        <li>{{ t('helpCenter.scanShared.tipDistance') }}</li>
        <li>{{ t('helpCenter.scanShared.tipNarrow') }}</li>
        <li>{{ t('helpCenter.scanShared.tipHttps') }}</li>
        <li>{{ t('helpCenter.scanShared.tipNormalisation') }}</li>
      </ul>

      <NeutralContainer class="space-y-2">
        <FieldLabel>{{ t('inventory.manage.scanLabel') }}</FieldLabel>
        <div class="flex items-center gap-2">
          <TextInput model-value="" :placeholder="t('inventory.manage.scanPlaceholder')" class="flex-1" disabled />
          <ScanButton/>
          <PrimaryButton :icon="['fas', 'magnifying-glass']">
            {{ t('inventory.manage.scanSubmit') }}
          </PrimaryButton>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryManage.cardsTitle')">
      <p>{{ t('helpCenter.inventoryManage.cardsText') }}</p>
      <DummyInventoryCards />
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryManage.itemsTitle')">
      <p>{{ t('helpCenter.inventoryManage.itemsText') }}</p>
      <p>{{ t('helpCenter.inventoryManage.itemsExternal') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.INVENTORY_MANAGER]" :label="t('helpCenter.permissionLabel.inventoryManage')">
      <HelpSection :title="t('helpCenter.inventoryManage.assignTitle')">
        <p>{{ t('helpCenter.inventoryManage.assignText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.inventoryManage.tip') }}</HelpTip>
  </HelpArticle>
</template>
