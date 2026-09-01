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
import ExampleItemCard from './listhelp/ExampleItemCard.vue'
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
      <ExampleItemCard name="Roter Handschuh (links)" found-at="18.05.2026" has-image
                       :claim-label="t('lostAndFound.claimedByYou')">
        <HelpPermissionGuard :permissions="[StationPermission.LOST_AND_FOUND_MANAGE]" :label="t('helpCenter.permissionLabel.lostAndFoundManage')">
          <div class="flex gap-2">
            <SuccessButton :icon="['fas', 'circle-check']" class="text-xs flex-1" disabled>
              {{ t('lostAndFound.provided') }}
            </SuccessButton>
            <DeleteButton disabled/>
          </div>
        </HelpPermissionGuard>
        <SecondaryButton :icon="['fas', 'rotate-left']" class="text-xs w-full" disabled>
          {{ t('lostAndFound.release') }}
        </SecondaryButton>
      </ExampleItemCard>

      <ExampleItemCard name="Blaue Trinkflasche" found-at="15.05.2026" has-image
                       :claim-label="t('lostAndFound.claimedBy', {name: 'Max Mustermann'})"/>

      <ExampleItemCard name="Schwarze Mütze" found-at="12.05.2026" :has-image="false">
        <div class="flex gap-2">
          <SuccessButton class="text-xs flex-1" disabled>
            {{ t('lostAndFound.claim') }}
          </SuccessButton>
          <DeleteButton disabled/>
        </div>
        <SecondaryButton :icon="['fas', 'camera']" class="text-xs w-full" disabled>
          {{ t('lostAndFound.addImage') }}
        </SecondaryButton>
      </ExampleItemCard>
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

      <HelpSection :title="t('helpCenter.lostAndFound.photoTitle')">
        <p>{{ t('helpCenter.lostAndFound.photoText') }}</p>
        <p>{{ t('helpCenter.lostAndFound.photoText2') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpSection :title="t('helpCenter.lostAndFound.claimTitle')">
      <p>{{ t('helpCenter.lostAndFound.claimText') }}</p>
      <p>{{ t('helpCenter.lostAndFound.claimForText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.lostAndFound.releaseTitle')">
      <p>{{ t('helpCenter.lostAndFound.releaseText') }}</p>
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
