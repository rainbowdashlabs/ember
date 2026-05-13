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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

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
    </HelpSection>

    <!-- Dummy: Requirements list -->
    <div class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('inventory.requirements.title') }}</SectionHeader>
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('inventory.requirements.add') }}
        </PrimaryButton>
      </div>

      <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between">
          <SubHeader>
            <span class="text-xs uppercase tracking-wide text-(--text-muted) mr-2">{{ t('inventory.requirements.role') }}</span>
            {{ t('membersCreate.roleMember') }}
          </SubHeader>
          <SecondaryButton class="text-sm">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('inventory.requirements.addItem') }}
          </SecondaryButton>
        </div>
        <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5" />
          <div class="text-sm">Helme</div>
          <div class="text-sm text-center font-medium">1</div>
          <DeleteButton />
        </div>
        <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5" />
          <div class="text-sm">Jacken</div>
          <div class="text-sm text-center font-medium">1</div>
          <DeleteButton />
        </div>
      </NeutralContainer>

      <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between">
          <SubHeader>
            <span class="text-xs uppercase tracking-wide text-(--text-muted) mr-2">{{ t('inventory.requirements.group') }}</span>
            Anfänger
          </SubHeader>
          <SecondaryButton class="text-sm">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('inventory.requirements.addItem') }}
          </SecondaryButton>
        </div>
        <div class="grid grid-cols-[auto_1fr_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
          <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5" />
          <div class="text-sm">Stiefel</div>
          <div class="text-sm text-center font-medium">1</div>
          <DeleteButton />
        </div>
      </NeutralContainer>
    </div>

    <HelpTip>{{ t('helpCenter.inventoryRequirements.tip') }}</HelpTip>
  </HelpArticle>
</template>
