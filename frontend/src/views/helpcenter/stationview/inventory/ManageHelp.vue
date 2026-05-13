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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.inventoryManage.title')" :subtitle="t('helpCenter.inventoryManage.subtitle')">
    <HelpSection :title="t('helpCenter.inventoryManage.whatIs')">
      <p>{{ t('helpCenter.inventoryManage.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryManage.createTitle')">
      <p>{{ t('helpCenter.inventoryManage.createName') }}</p>
      <p>{{ t('helpCenter.inventoryManage.createType') }}</p>
      <p>{{ t('helpCenter.inventoryManage.createSizes') }}</p>
    </HelpSection>

    <!-- Dummy: Inventory cards -->
    <div class="space-y-3">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('inventory.manage.title') }}</SectionHeader>
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('inventory.manage.create') }}
        </PrimaryButton>
      </div>

      <NeutralContainer class="cursor-pointer hover:ring-1 hover:ring-primary/30 transition-all">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-medium">Helme</span>
            <span class="ml-2 text-xs text-(--text-muted)">{{ t('inventory.manage.type.INTERNAL') }}</span>
            <span class="ml-2 text-xs text-secondary-accent dark:text-secondary">{{ t('inventory.manage.withSizes') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton />
            <DeleteButton />
          </div>
        </div>
        <div class="text-xs text-(--text-muted) mt-1">
          {{ t('inventory.manage.itemCount', { count: 24 }) }}
          &middot; <span class="text-error">{{ t('inventory.manage.lostCount', { count: 1 }) }}</span>
        </div>
      </NeutralContainer>

      <NeutralContainer class="cursor-pointer hover:ring-1 hover:ring-primary/30 transition-all">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-medium">Jacken</span>
            <span class="ml-2 text-xs text-(--text-muted)">{{ t('inventory.manage.type.EXTERNAL') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton />
            <DeleteButton />
          </div>
        </div>
        <div class="text-xs text-(--text-muted) mt-1">
          {{ t('inventory.manage.itemCount', { count: 18 }) }}
          &middot; <span class="text-info-accent dark:text-info">{{ t('inventory.manage.procurementCount', { count: 2 }) }}</span>
        </div>
      </NeutralContainer>

      <NeutralContainer class="cursor-pointer hover:ring-1 hover:ring-primary/30 transition-all">
        <div class="flex items-center justify-between">
          <div>
            <span class="font-medium">Stiefel</span>
            <span class="ml-2 text-xs text-(--text-muted)">{{ t('inventory.manage.type.MIXED') }}</span>
            <span class="ml-2 text-xs text-secondary-accent dark:text-secondary">{{ t('inventory.manage.withSizes') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton />
            <DeleteButton />
          </div>
        </div>
        <div class="text-xs text-(--text-muted) mt-1">
          {{ t('inventory.manage.itemCount', { count: 30 }) }}
        </div>
      </NeutralContainer>
    </div>

    <HelpSection :title="t('helpCenter.inventoryManage.itemsTitle')">
      <p>{{ t('helpCenter.inventoryManage.itemsText') }}</p>
      <p>{{ t('helpCenter.inventoryManage.itemsExternal') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.inventoryManage.assignTitle')">
      <p>{{ t('helpCenter.inventoryManage.assignText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.inventoryManage.tip') }}</HelpTip>
  </HelpArticle>
</template>
