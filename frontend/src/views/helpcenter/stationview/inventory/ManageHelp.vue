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
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
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

    <!-- Dummy: Barcode scanner -->
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

    <!-- Dummy: Inventory cards -->
    <HelpSection :title="t('helpCenter.inventoryManage.cardsTitle')">
      <p>{{ t('helpCenter.inventoryManage.cardsText') }}</p>

    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('inventory.manage.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']">
          {{ t('inventory.manage.create') }}
        </PrimaryButton>
      </div>

      <NeutralContainer clickable>
        <div class="flex items-center justify-between">
          <div>
            <span class="font-medium">Helme</span>
            <MutedText class="ml-2">{{ t('inventory.manage.type.INTERNAL') }}</MutedText>
            <span class="ml-2 text-xs text-secondary-accent dark:text-secondary">{{ t('inventory.manage.withSizes') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton />
            <DeleteButton />
          </div>
        </div>
        <MutedText tag="div" class="mt-1">
          {{ t('inventory.manage.itemCount', { count: 24 }) }}
          &middot; <span class="text-error">{{ t('inventory.manage.lostCount', { count: 1 }) }}</span>
          &middot; <span class="text-secondary-accent dark:text-secondary">{{ t('inventory.manage.lentOutCount', { count: 2 }) }}</span>
        </MutedText>
      </NeutralContainer>

      <NeutralContainer clickable>
        <div class="flex items-center justify-between">
          <div>
            <span class="font-medium">Jacken</span>
            <MutedText class="ml-2">{{ t('inventory.manage.type.EXTERNAL') }}</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <EditButton />
            <DeleteButton />
          </div>
        </div>
        <MutedText tag="div" class="mt-1">
          {{ t('inventory.manage.itemCount', { count: 18 }) }}
          &middot; <span class="text-info-accent dark:text-info">{{ t('inventory.manage.procurementCount', { count: 2 }) }}</span>
        </MutedText>
      </NeutralContainer>

      <NeutralContainer clickable>
        <div class="flex items-center justify-between">
          <div>
            <span class="font-medium">Stiefel</span>
            <MutedText class="ml-2">{{ t('inventory.manage.type.MIXED') }}</MutedText>
            <span class="ml-2 text-xs text-secondary-accent dark:text-secondary">{{ t('inventory.manage.withSizes') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton />
            <DeleteButton />
          </div>
        </div>
        <MutedText tag="div" class="mt-1">
          {{ t('inventory.manage.itemCount', { count: 30 }) }}
        </MutedText>
      </NeutralContainer>
    </div>
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
