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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import ItemsTableDummy from '@/views/helpcenter/stationview/inventory/edithelp/ItemsTableDummy.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryEdit.title')" :subtitle="t('helpCenter.inventoryEdit.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryEdit.whatShown')">
      <p>{{ t('helpCenter.inventoryEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.backTitle')">
      <p>{{ t('helpCenter.inventoryEdit.backText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.fieldsTitle')">
      <p>{{ t('helpCenter.inventoryEdit.fieldsText') }}</p>
    </HelpSection>

    <!-- Dummy: Settings section -->
    <HelpSection :title="t('helpCenter.inventoryEdit.settingsTitle')">
      <p>{{ t('helpCenter.inventoryEdit.settingsText') }}</p>

      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('helpCenter.inventoryEdit.dummySettings') }}</SubHeader>
        <div class="grid sm:grid-cols-2 gap-4">
          <div class="space-y-1">
            <FieldLabel>{{ t('inventory.manage.name') }}</FieldLabel>
            <TextInput model-value="Helme" disabled />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('inventory.manage.typeLabel') }}</FieldLabel>
            <SelectInput model-value="INTERNAL" disabled>
              <option value="INTERNAL">{{ t('inventory.manage.type.INTERNAL') }}</option>
            </SelectInput>
          </div>
        </div>
        <PrimaryButton>{{ t('common.save') }}</PrimaryButton>
      </NeutralContainer>
    </HelpSection>

    <!-- Dummy: Sizes section -->
    <HelpSection :title="t('helpCenter.inventoryEdit.sizesTitle')">
      <p>{{ t('helpCenter.inventoryEdit.sizesText') }}</p>
      <p>{{ t('helpCenter.inventoryEdit.sizesDragText') }}</p>

      <NeutralContainer class="space-y-2">
        <SubHeader>{{ t('helpCenter.inventoryEdit.dummySizes') }}</SubHeader>
        <div class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <span class="text-sm">S</span>
          </div>
          <div class="flex gap-1">
            <EditButton />
            <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.delete')" hover="error" class="text-sm" />
          </div>
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <span class="text-sm">M</span>
          </div>
          <div class="flex gap-1">
            <EditButton />
            <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.delete')" hover="error" class="text-sm" />
          </div>
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <span class="text-sm">L</span>
          </div>
          <div class="flex gap-1">
            <EditButton />
            <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.delete')" hover="error" class="text-sm" />
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.itemsTitle')">
      <p>{{ t('helpCenter.inventoryEdit.itemsText') }}</p>
    </HelpSection>

    <ItemsTableDummy />

    <HelpSection :title="t('helpCenter.inventoryEdit.assignTitle')">
      <p>{{ t('helpCenter.inventoryEdit.assignText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.scanShared.title')">
      <p>{{ t('helpCenter.scanShared.intro') }}</p>
      <ul class="list-disc pl-5 space-y-1">
        <li>{{ t('helpCenter.scanShared.tipPermission') }}</li>
        <li>{{ t('helpCenter.scanShared.tipDistance') }}</li>
        <li>{{ t('helpCenter.scanShared.tipNarrow') }}</li>
        <li>{{ t('helpCenter.scanShared.tipNormalisation') }}</li>
      </ul>

      <NeutralContainer class="space-y-2">
        <FieldLabel>{{ t('inventory.edit.itemInternalId') }}</FieldLabel>
        <div class="flex items-center gap-2">
          <TextInput model-value="INV-0042" class="flex-1" disabled/>
          <ScanButton/>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.historyTitle')">
      <p>{{ t('helpCenter.inventoryEdit.historyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
