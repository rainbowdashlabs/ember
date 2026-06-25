/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'

const {t} = useI18n()
</script>

<template>
  <HelpSection :title="t('helpCenter.itemDetail.metadataTitle')">
    <p>{{ t('helpCenter.itemDetail.metadataText') }}</p>

    <NeutralContainer class="space-y-3">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('helpCenter.itemDetail.dummyItemName') }}</SubHeader>
        <SecondaryButton :icon="['fas', 'pen']">
          {{ t('itemDetail.edit') }}
        </SecondaryButton>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.internalId') }}</FieldLabel>
          <span class="font-mono">INV-0001</span>
        </div>
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.size') }}</FieldLabel>
          <span>M</span>
        </div>
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.status') }}</FieldLabel>
          <SuccessBadge>{{ t('itemDetail.active') }}</SuccessBadge>
        </div>
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.assignedTo') }}</FieldLabel>
          <span>Max Mustermann</span>
        </div>
      </div>
    </NeutralContainer>

    <NeutralContainer class="space-y-3 mt-3">
      <SubHeader>{{ t('helpCenter.itemDetail.dummyLostItemName') }}</SubHeader>
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-4 text-sm">
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.internalId') }}</FieldLabel>
          <span class="font-mono">INV-0015</span>
        </div>
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.size') }}</FieldLabel>
          <span>L</span>
        </div>
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.status') }}</FieldLabel>
          <ErrorBadge>{{ t('helpCenter.itemDetail.dummyLostSince') }}</ErrorBadge>
        </div>
        <div>
          <FieldLabel class="text-xs">{{ t('itemDetail.assignedTo') }}</FieldLabel>
          <span>Erika Musterfrau</span>
        </div>
      </div>
    </NeutralContainer>
  </HelpSection>
</template>
