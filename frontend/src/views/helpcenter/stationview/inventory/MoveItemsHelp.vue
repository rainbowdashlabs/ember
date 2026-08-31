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
import SelectInput from '@/components/input/select/SelectInput.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryMove.title')" :subtitle="t('helpCenter.inventoryMove.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryMove.whatShown')">
      <p>{{ t('helpCenter.inventoryMove.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMove.keepsTitle')">
      <p>{{ t('helpCenter.inventoryMove.keepsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMove.sizeTitle')">
      <p>{{ t('helpCenter.inventoryMove.sizeText') }}</p>

      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('inventory.move.target') }}</SubHeader>
        <div class="space-y-1">
          <FieldLabel>{{ t('inventory.move.targetLabel') }}</FieldLabel>
          <SelectInput disabled model-value="1">
            <option value="1">Bundhose schwer</option>
          </SelectInput>
        </div>
        <ul class="divide-y divide-(--border)">
          <li class="flex items-center gap-3 py-2">
            <CheckboxInput :model-value="true"/>
            <div>
              <p class="text-sm">Bundhose</p>
              <p class="text-xs text-(--text-muted)">BH-0007 &middot; {{ t('inventory.move.sizeKept', {size: '152'}) }}</p>
            </div>
          </li>
          <li class="flex items-center gap-3 py-2">
            <CheckboxInput :model-value="true"/>
            <div>
              <p class="text-sm">Bundhose</p>
              <p class="text-xs text-(--text-muted)">BH-0008 &middot; {{ t('inventory.move.sizeCleared', {size: '188'}) }}</p>
            </div>
          </li>
        </ul>
        <PrimaryButton :icon="['fas', 'right-left']">{{ t('inventory.move.submit', {count: 2}) }}</PrimaryButton>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryMove.whyTitle')">
      <p>{{ t('helpCenter.inventoryMove.whyText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryMove.tip') }}</HelpTip>
  </HelpArticle>
</template>
