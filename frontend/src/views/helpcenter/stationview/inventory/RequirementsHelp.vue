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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RequirementGroupCard from '@/views/stationview/inventory/requirementsview/RequirementGroupCard.vue'
import {groups, inventoryName} from './requirementshelp/fixtures'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryRequirements.title')" :subtitle="t('helpCenter.inventoryRequirements.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryRequirements.whatIs')">
      <p>{{ t('helpCenter.inventoryRequirements.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryRequirements.createTitle')">
      <p>{{ t('helpCenter.inventoryRequirements.createRoleOrGroup') }}</p>
      <p>{{ t('helpCenter.inventoryRequirements.createInventory') }}</p>
      <p>{{ t('helpCenter.inventoryRequirements.createQuantity') }}</p>
      <div class="space-y-4">
        <div class="flex items-center justify-end">
          <PrimaryButton :icon="['fas', 'plus']">{{ t('inventory.requirements.add') }}</PrimaryButton>
        </div>
        <RequirementGroupCard
            v-for="group in groups"
            :key="`${group.type}-${group.key}`"
            :group="group"
            :inventory-name="inventoryName"
        />
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryRequirements.tip') }}</HelpTip>
  </HelpArticle>
</template>
