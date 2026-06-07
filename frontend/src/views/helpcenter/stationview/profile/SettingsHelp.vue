/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ThemeSelector from '@/components/theme/ThemeSelector.vue'

const {t} = useI18n()

const dummyEmailEnabled = ref(true)
</script>

<template>
  <HelpArticle :title="t('helpCenter.settings.title')" :subtitle="t('helpCenter.settings.subtitle')">
    <!-- Theme section -->
    <HelpSection :title="t('helpCenter.settings.themeTitle')">
      <p>{{ t('helpCenter.settings.themeText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('theme.title') }}</SubHeader>
      <ThemeSelector :show-themes="true" :show-feel="true"/>
      <p class="text-sm text-(--text-muted)">{{ t('theme.controlledByStation') }}</p>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.settings.emailTitle')">
      <p>{{ t('helpCenter.settings.emailText') }}</p>
    </HelpSection>

    <!-- Dummy: Mail provider info -->
    <InfoContainer class="space-y-2">
      <p class="text-sm">
        {{ t('userSettings.mailProviderInfo', {provider: 'Beispiel Mail GmbH'}) }}
      </p>
      <p class="text-xs">
        <a href="#" class="text-primary hover:underline">
          {{ t('userSettings.mailProviderPrivacy') }}
        </a>
      </p>
    </InfoContainer>

    <!-- Dummy: Email settings -->
    <NeutralContainer class="space-y-4">
      <SubHeader class="text-sm">{{ t('userSettings.emailTitle') }}</SubHeader>

      <!-- Master email toggle -->
      <div class="flex items-center justify-between">
        <div>
          <span class="text-sm font-medium">{{ t('userSettings.emailEnabled') }}</span>
          <p class="text-xs text-(--text-muted)">{{ t('userSettings.emailEnabledHint') }}</p>
          <p class="text-xs text-(--text-muted)">
            {{ t('userSettings.emailConsent', {provider: 'Beispiel Mail GmbH'}) }}
          </p>
        </div>
        <ToggleInput v-model="dummyEmailEnabled"/>
      </div>

      <template v-if="dummyEmailEnabled">
        <SubHeader class="text-sm pt-2">{{ t('userSettings.notifications') }}</SubHeader>
        <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

        <!-- Header row -->
        <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
          <span></span>
          <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
          <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
          <span class="w-12 text-center">{{ t('userSettings.columnFeed') }}</span>
        </div>

        <!-- All 8 notification type rows -->
        <div class="space-y-3">
          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyNews') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyNewsHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
          </div>

          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyComments') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyCommentsHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
          </div>

          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyEvents') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyEventsHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
          </div>

          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyEventStatus') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyEventStatusHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
          </div>

          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyExchanges') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyExchangesHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
          </div>

          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyGroups') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyGroupsHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
          </div>

          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyProfile') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyProfileHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="true" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
          </div>

          <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center py-1">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyProcurement') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyProcurementHint') }}</p>
            </div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
            <div class="w-12 flex justify-center"><ToggleInput :model-value="false" disabled/></div>
          </div>
        </div>
      </template>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.settings.typesTitle')">
      <p>{{ t('helpCenter.settings.typesText') }}</p>
      <p>{{ t('helpCenter.settings.typeNews') }}</p>
      <p>{{ t('helpCenter.settings.typeComments') }}</p>
      <p>{{ t('helpCenter.settings.typeEvents') }}</p>
      <p>{{ t('helpCenter.settings.typeEventChanges') }}</p>
      <p>{{ t('helpCenter.settings.typeExchanges') }}</p>
      <p>{{ t('helpCenter.settings.typeGroups') }}</p>
      <p>{{ t('helpCenter.settings.typeProfile') }}</p>
      <p>{{ t('helpCenter.settings.typeProcurement') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.settings.tip') }}</HelpTip>
  </HelpArticle>
</template>
