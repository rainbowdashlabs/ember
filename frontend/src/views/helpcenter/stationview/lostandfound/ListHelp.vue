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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.lostAndFound.title')" :subtitle="t('helpCenter.lostAndFound.subtitle')">
    <HelpSection :title="t('helpCenter.lostAndFound.whatIs')">
      <p>{{ t('helpCenter.lostAndFound.whatIsText') }}</p>
    </HelpSection>

    <!-- Dummy: Lost and found list -->
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('lostAndFound.title') }}</SectionHeader>
      <HelpPermissionGuard :permissions="[StationPermission.LOST_AND_FOUND_CREATE]" :label="t('helpCenter.permissionLabel.lostAndFoundCreate')">
        <PrimaryButton :icon="['fas', 'plus']" disabled>
          {{ t('lostAndFound.create') }}
        </PrimaryButton>
      </HelpPermissionGuard>
    </div>

    <div class="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      <NeutralContainer class="space-y-3">
        <div class="w-full h-28 rounded bg-bg-light-accent dark:bg-bg-dark-accent flex items-center justify-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'image']" class="text-2xl"/>
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Roter Handschuh (links)</p>
          <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: 18.05.2026</p>
          <SuccessBadge>{{ t('lostAndFound.claimedByYou') }}</SuccessBadge>
        </div>
        <HelpPermissionGuard :permissions="[StationPermission.LOST_AND_FOUND_MANAGE]" :label="t('helpCenter.permissionLabel.lostAndFoundManage')">
          <div class="flex gap-2">
            <SuccessButton :icon="['fas', 'circle-check']" class="text-xs flex-1" disabled>
              {{ t('lostAndFound.provided') }}
            </SuccessButton>
            <DeleteButton disabled/>
          </div>
        </HelpPermissionGuard>
      </NeutralContainer>

      <NeutralContainer class="space-y-3">
        <div class="w-full h-28 rounded bg-bg-light-accent dark:bg-bg-dark-accent flex items-center justify-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'image']" class="text-2xl"/>
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Blaue Trinkflasche</p>
          <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: 15.05.2026</p>
          <SuccessBadge>{{ t('lostAndFound.claimedBy', {name: 'Max Mustermann'}) }}</SuccessBadge>
        </div>
      </NeutralContainer>

      <NeutralContainer class="space-y-3">
        <div class="w-full h-28 rounded bg-bg-light-accent dark:bg-bg-dark-accent flex items-center justify-center text-(--text-muted)">
          <font-awesome-icon :icon="['fas', 'box-open']" class="text-2xl"/>
        </div>
        <div class="space-y-1">
          <p class="text-sm font-medium">Schwarze Mütze</p>
          <p class="text-xs text-(--text-muted)">{{ t('lostAndFound.foundAt') }}: 12.05.2026</p>
        </div>
        <div class="flex gap-2">
          <SuccessButton class="text-xs flex-1" disabled>
            {{ t('lostAndFound.claim') }}
          </SuccessButton>
          <DeleteButton disabled/>
        </div>
      </NeutralContainer>
    </div>

    <HelpPermissionGuard :permissions="[StationPermission.LOST_AND_FOUND_CREATE]" :label="t('helpCenter.permissionLabel.lostAndFoundCreate')">
      <HelpSection :title="t('helpCenter.lostAndFound.reportTitle')">
        <p>{{ t('helpCenter.lostAndFound.reportText') }}</p>
      </HelpSection>

      <!-- Dummy: Create form -->
      <NeutralContainer class="space-y-3">
        <p class="text-sm font-semibold">{{ t('lostAndFound.createTitle') }}</p>
        <div class="space-y-1">
          <FieldLabel>{{ t('lostAndFound.image') }}</FieldLabel>
          <div class="flex gap-2">
            <SecondaryButton :icon="['fas', 'upload']" class="flex-1" disabled>
              {{ t('lostAndFound.uploadImage') }}
            </SecondaryButton>
            <SecondaryButton :icon="['fas', 'camera']" class="flex-1" disabled>
              {{ t('lostAndFound.takePhoto') }}
            </SecondaryButton>
          </div>
        </div>
        <div>
          <FieldLabel hint class="mb-1">{{ t('lostAndFound.description') }}</FieldLabel>
          <TextAreaInput :model-value="''" :placeholder="t('lostAndFound.descriptionPlaceholder')" disabled/>
        </div>
        <div>
          <FieldLabel hint class="mb-1">{{ t('lostAndFound.foundAt') }}</FieldLabel>
          <DateInput model-value="" disabled/>
        </div>
      </NeutralContainer>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.lostAndFound.claimTitle')">
      <p>{{ t('helpCenter.lostAndFound.claimText') }}</p>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.LOST_AND_FOUND_MANAGE]" :label="t('helpCenter.permissionLabel.lostAndFoundManage')">
      <HelpSection :title="t('helpCenter.lostAndFound.managerTitle')">
        <p>{{ t('helpCenter.lostAndFound.managerText') }}</p>
        <p>{{ t('helpCenter.lostAndFound.providedText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.lostAndFound.tip') }}</HelpTip>
  </HelpArticle>
</template>
