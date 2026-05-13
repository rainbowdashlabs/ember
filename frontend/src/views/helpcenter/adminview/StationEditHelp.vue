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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminStationEdit.title')" :subtitle="t('helpCenter.adminStationEdit.subtitle')">
    <HelpSection :title="t('helpCenter.adminStationEdit.whatShown')">
      <p>{{ t('helpCenter.adminStationEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.nameTitle')">
      <p>{{ t('helpCenter.adminStationEdit.nameText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminStationEdit.managerTitle')">
      <p>{{ t('helpCenter.adminStationEdit.managerText') }}</p>
    </HelpSection>

    <!-- Dummy: Station edit form -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('adminStations.editTitle') }}</SectionHeader>

      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('adminStations.name') }}</label>
        <div class="rounded-lg border border-bg-light-accent dark:border-bg-dark-accent px-3 py-2 text-sm">
          DLRG Musterstadt
        </div>
      </div>

      <div class="space-y-2">
        <label class="block text-sm font-medium">{{ t('adminStations.managerEmail') }}</label>
        <SuccessContainer class="flex items-center justify-between">
          <div>
            <div class="font-medium">Max Mustermann</div>
            <div class="text-sm text-(--text-muted)">max@mustermann.de</div>
            <div class="text-xs mt-1">
              <span class="text-success">
                <font-awesome-icon :icon="['fas', 'check']" class="mr-1"/>{{ t('adminStations.accountReady') }}
              </span>
            </div>
          </div>
          <EditButton/>
        </SuccessContainer>
      </div>

      <PrimaryButton>{{ t('adminStations.save') }}</PrimaryButton>
    </NeutralContainer>

    <!-- Dummy: Back button -->
    <SecondaryButton>
      <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2"/>
      {{ t('adminStations.back') }}
    </SecondaryButton>

    <HelpTip>{{ t('helpCenter.adminStationEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
