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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryEdit.title')" :subtitle="t('helpCenter.inventoryEdit.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryEdit.whatShown')">
      <p>{{ t('helpCenter.inventoryEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.settingsTitle')">
      <p>{{ t('helpCenter.inventoryEdit.settingsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.sizesTitle')">
      <p>{{ t('helpCenter.inventoryEdit.sizesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.itemsTitle')">
      <p>{{ t('helpCenter.inventoryEdit.itemsText') }}</p>
    </HelpSection>

    <!-- Dummy: Items table -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('inventory.edit.itemsTitle') }}</SubHeader>
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('inventory.edit.addItem') }}
        </PrimaryButton>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
              <th class="px-3 py-2 font-medium">{{ t('inventory.edit.colName') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.edit.colId') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.edit.colSize') }}</th>
              <th class="px-3 py-2 font-medium">{{ t('inventory.edit.colAssigned') }}</th>
              <th class="px-3 py-2"></th>
            </tr>
          </thead>
          <tbody>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5 font-medium">Helm</td>
              <td class="px-3 py-2.5 text-(--text-muted)">INV-0001</td>
              <td class="px-3 py-2.5 text-(--text-muted)">M</td>
              <td class="px-3 py-2.5"><span class="text-primary font-medium">Max Mustermann</span></td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex items-center justify-end gap-0.5">
                  <EditButton />
                  <DeleteButton />
                </div>
              </td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
              <td class="px-3 py-2.5 font-medium">Helm</td>
              <td class="px-3 py-2.5 text-(--text-muted)">INV-0002</td>
              <td class="px-3 py-2.5 text-(--text-muted)">L</td>
              <td class="px-3 py-2.5"><span class="text-(--text-muted)">&ndash;</span></td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex items-center justify-end gap-0.5">
                  <EditButton />
                  <DeleteButton />
                </div>
              </td>
            </tr>
            <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 opacity-60">
              <td class="px-3 py-2.5 font-medium">
                Helm <span class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} (01.05.2026)</span>
              </td>
              <td class="px-3 py-2.5 text-(--text-muted)">INV-0003</td>
              <td class="px-3 py-2.5 text-(--text-muted)">S</td>
              <td class="px-3 py-2.5"><span class="text-primary font-medium">Erika Musterfrau</span></td>
              <td class="px-3 py-2.5 text-right">
                <div class="flex items-center justify-end gap-0.5">
                  <EditButton />
                  <DeleteButton />
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.inventoryEdit.assignTitle')">
      <p>{{ t('helpCenter.inventoryEdit.assignText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryEdit.historyTitle')">
      <p>{{ t('helpCenter.inventoryEdit.historyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
