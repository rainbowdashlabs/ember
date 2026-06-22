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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryEdit.title')" :subtitle="t('helpCenter.inventoryEdit.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryEdit.whatShown')">
      <p>{{ t('helpCenter.inventoryEdit.whatShownText') }}</p>
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
            <IconButton :icon="['fas', 'xmark']" :label="t('common.delete')" class="text-(--text-muted) hover:text-error text-sm" />
          </div>
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <span class="text-sm">M</span>
          </div>
          <div class="flex gap-1">
            <EditButton />
            <IconButton :icon="['fas', 'xmark']" :label="t('common.delete')" class="text-(--text-muted) hover:text-error text-sm" />
          </div>
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) cursor-grab" />
            <span class="text-sm">L</span>
          </div>
          <div class="flex gap-1">
            <EditButton />
            <IconButton :icon="['fas', 'xmark']" :label="t('common.delete')" class="text-(--text-muted) hover:text-error text-sm" />
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.itemsTitle')">
      <p>{{ t('helpCenter.inventoryEdit.itemsText') }}</p>
    </HelpSection>

    <!-- Dummy: Items table -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SubHeader>{{ t('inventory.edit.itemsTitle') }}</SubHeader>
        <div class="flex gap-2">
          <SecondaryButton :icon="['fas', 'user-plus']">
            {{ t('helpCenter.inventoryEdit.dummyQuickAssign') }}
          </SecondaryButton>
          <PrimaryButton :icon="['fas', 'plus']">
            {{ t('inventory.edit.addItem') }}
          </PrimaryButton>
        </div>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('inventory.edit.colName') }}</Th>
              <Th>{{ t('inventory.edit.colId') }}</Th>
              <Th>{{ t('inventory.edit.colSize') }}</Th>
              <Th>{{ t('inventory.edit.colAssigned') }}</Th>
              <th class="px-3 py-2"></th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td class="font-medium">Helm</Td>
              <Td muted>INV-0001</Td>
              <Td muted>M</Td>
              <Td><span class="text-primary font-medium">Max Mustermann</span></Td>
              <Td align="right">
                <div class="flex items-center justify-end gap-0.5">
                  <EditButton />
                  <DeleteButton />
                </div>
              </Td>
            </TRow>
            <TRow>
              <Td class="font-medium">Helm</Td>
              <Td muted>INV-0002</Td>
              <Td muted>L</Td>
              <Td><span class="text-(--text-muted)">&ndash;</span></Td>
              <Td align="right">
                <div class="flex items-center justify-end gap-0.5">
                  <EditButton />
                  <DeleteButton />
                </div>
              </Td>
            </TRow>
            <TRow class="opacity-60">
              <Td class="font-medium">
                Helm <span class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} (01.05.2026)</span>
              </Td>
              <Td muted>INV-0003</Td>
              <Td muted>S</Td>
              <Td><span class="text-primary font-medium">Erika Musterfrau</span></Td>
              <Td align="right">
                <div class="flex items-center justify-end gap-0.5">
                  <EditButton />
                  <DeleteButton />
                </div>
              </Td>
            </TRow>
          </tbody>
        </table>
      </div>
    </NeutralContainer>

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
