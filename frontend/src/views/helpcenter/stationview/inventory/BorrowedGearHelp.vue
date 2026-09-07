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
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryBorrowed.title')" :subtitle="t('helpCenter.inventoryBorrowed.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryBorrowed.whatShown')">
      <p>{{ t('helpCenter.inventoryBorrowed.whatShownText') }}</p>

      <NeutralContainer>
        <div class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <THead>
                <Th>{{ t('inventory.borrowed.colName') }}</Th>
                <Th>{{ t('inventory.borrowed.colId') }}</Th>
                <Th>{{ t('inventory.borrowed.colOwner') }}</Th>
                <Th>{{ t('inventory.borrowed.colDue') }}</Th>
              </THead>
            </thead>
            <tbody>
              <TRow>
                <Td>Handfunkgerät</Td>
                <Td muted>FG-0042</Td>
                <Td><PrimaryBadge>Wache Nord</PrimaryBadge></Td>
                <Td muted>14.03.2026</Td>
              </TRow>
              <TRow>
                <Td>Wärmebildkamera</Td>
                <Td muted>WBK-0003</Td>
                <Td><PrimaryBadge>Wache Süd</PrimaryBadge></Td>
                <Td muted>{{ t('inventory.borrowed.noDueDate') }}</Td>
              </TRow>
            </tbody>
          </table>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryBorrowed.whyOneShelfTitle')">
      <p>{{ t('helpCenter.inventoryBorrowed.whyOneShelfText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryBorrowed.renameTitle')">
      <p>{{ t('helpCenter.inventoryBorrowed.renameText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryBorrowed.useTitle')">
      <p>{{ t('helpCenter.inventoryBorrowed.useText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryBorrowed.snapshotTitle')">
      <p>{{ t('helpCenter.inventoryBorrowed.snapshotText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryBorrowed.checkTitle')">
      <p>{{ t('helpCenter.inventoryBorrowed.checkText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryBorrowed.returnTitle')">
      <p>{{ t('helpCenter.inventoryBorrowed.returnText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryBorrowed.tip') }}</HelpTip>
  </HelpArticle>
</template>
