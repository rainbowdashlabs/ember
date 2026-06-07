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
import SuccessContainer from '@/components/container/SuccessContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStationEdit.title')" :subtitle="t('helpCenter.adminStationEdit.subtitle')">
    <HelpSection :title="t('helpCenter.adminStationEdit.whatShown')">
      <p>{{ t('helpCenter.adminStationEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.createModeTitle')">
      <p>{{ t('helpCenter.adminStationEdit.createModeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.nameTitle')">
      <p>{{ t('helpCenter.adminStationEdit.nameText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.managerTitle')">
      <p>{{ t('helpCenter.adminStationEdit.managerText') }}</p>
    </HelpSection>

    <!-- Dummy: Station edit form -->
    <HelpSection :title="t('helpCenter.adminStationEdit.exampleTitle')">
      <!-- Back button above the form (matches real view) -->
      <SecondaryButton :icon="['fas', 'chevron-left']" class="mb-4">
        {{ t('adminStations.back') }}
      </SecondaryButton>

      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminStations.editTitle') }}</SectionHeader>

        <div class="space-y-1">
          <FieldLabel>{{ t('adminStations.name') }}</FieldLabel>
          <TextInput :model-value="'DLRG Musterstadt'" :placeholder="t('adminStations.namePlaceholder')" />
        </div>

        <!-- Manager: account ready state -->
        <div class="space-y-2">
          <FieldLabel>{{ t('adminStations.managerEmail') }}</FieldLabel>
          <SuccessContainer class="flex items-center justify-between">
            <div>
              <div class="font-medium">Max Mustermann</div>
              <div class="text-sm text-(--text-muted)">max@mustermann.de</div>
              <div class="text-xs mt-1">
                <span class="text-success">
                  <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />{{ t('adminStations.accountReady') }}
                </span>
              </div>
            </div>
            <EditButton />
          </SuccessContainer>
        </div>

        <PrimaryButton>{{ t('adminStations.save') }}</PrimaryButton>
      </NeutralContainer>

      <!-- Manager: pending state -->
      <NeutralContainer class="space-y-4 mt-4">
        <SectionHeader>{{ t('adminStations.editTitle') }}</SectionHeader>

        <div class="space-y-1">
          <FieldLabel>{{ t('adminStations.name') }}</FieldLabel>
          <TextInput :model-value="'DLRG Beispielburg'" :placeholder="t('adminStations.namePlaceholder')" />
        </div>

        <div class="space-y-2">
          <FieldLabel>{{ t('adminStations.managerEmail') }}</FieldLabel>
          <InfoContainer class="flex items-center justify-between">
            <div>
              <div class="font-medium">neu@manager.de</div>
              <div class="text-sm text-(--text-muted)">neu@manager.de</div>
              <div class="text-xs mt-1">
                <span class="text-info">
                  <font-awesome-icon :icon="['fas', 'clock-rotate-left']" class="mr-1" />{{ t('adminStations.accountPending') }}
                </span>
              </div>
            </div>
            <EditButton />
          </InfoContainer>
        </div>

        <PrimaryButton>{{ t('adminStations.save') }}</PrimaryButton>
      </NeutralContainer>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminStationEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
