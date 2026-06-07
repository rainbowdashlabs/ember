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
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.sessions.title')" :subtitle="t('helpCenter.sessions.subtitle')">
    <HelpSection :title="t('helpCenter.sessions.whatIs')">
      <p>{{ t('helpCenter.sessions.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.sessions.listTitle')">
      <p>{{ t('helpCenter.sessions.listText') }}</p>
    </HelpSection>

    <!-- Dummy: Sessions list -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SubHeader>{{ t('userSettings.sessions') }}</SubHeader>
        <ErrorButton :icon="['fas', 'trash']" disabled>
          {{ t('userSettings.invalidateAll') }}
        </ErrorButton>
      </div>

      <div class="space-y-2">
        <!-- Current session -->
        <NeutralContainer class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fab', 'linux']" class="h-5 w-5 text-(--text-muted)"/>
              <font-awesome-icon :icon="['fab', 'firefox-browser']" class="h-5 w-5 text-(--text-muted)"/>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium">Firefox</span>
                <span class="text-xs text-(--text-muted)">Linux</span>
                <span class="text-xs font-semibold text-primary">{{ t('userSettings.currentSession') }}</span>
              </div>
              <p class="text-xs text-(--text-muted)">
                {{ t('userSettings.lastActive') }}: {{ t('userSettings.justNow') }}
                <span class="ml-2">{{ t('userSettings.created') }}: 07.06.2026, 08:15</span>
                <span class="ml-2">
                  <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-0.5"/>Berlin
                </span>
              </p>
            </div>
          </div>
        </NeutralContainer>

        <!-- Other session -->
        <NeutralContainer class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fab', 'apple']" class="h-5 w-5 text-(--text-muted)"/>
              <font-awesome-icon :icon="['fab', 'safari']" class="h-5 w-5 text-(--text-muted)"/>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium">Safari</span>
                <span class="text-xs text-(--text-muted)">macOS</span>
              </div>
              <p class="text-xs text-(--text-muted)">
                {{ t('userSettings.lastActive') }}: {{ t('userSettings.hoursAgo', {n: 3}) }}
                <span class="ml-2">{{ t('userSettings.created') }}: 05.06.2026, 14:30</span>
                <span class="ml-2">
                  <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-0.5"/>Hamburg
                </span>
              </p>
            </div>
          </div>
          <DeleteButton disabled/>
        </NeutralContainer>

        <!-- Another session -->
        <NeutralContainer class="flex items-center justify-between">
          <div class="flex items-center gap-2">
            <div class="flex items-center gap-2">
              <font-awesome-icon :icon="['fab', 'android']" class="h-5 w-5 text-(--text-muted)"/>
              <font-awesome-icon :icon="['fab', 'chrome']" class="h-5 w-5 text-(--text-muted)"/>
            </div>
            <div>
              <div class="flex items-center gap-2">
                <span class="text-sm font-medium">Chrome</span>
                <span class="text-xs text-(--text-muted)">Android</span>
              </div>
              <p class="text-xs text-(--text-muted)">
                {{ t('userSettings.lastActive') }}: {{ t('userSettings.daysAgo', {n: 2}) }}
                <span class="ml-2">{{ t('userSettings.created') }}: 01.06.2026, 09:00</span>
                <span class="ml-2">
                  <font-awesome-icon :icon="['fas', 'location-dot']" class="mr-0.5"/>{{ t('userSettings.unknownLocation') }}
                </span>
              </p>
            </div>
          </div>
          <DeleteButton disabled/>
        </NeutralContainer>
      </div>
    </NeutralContainer>

    <!-- GDPR export section -->
    <HelpSection :title="t('helpCenter.sessions.gdprTitle')">
      <p>{{ t('helpCenter.sessions.gdprText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('userSettings.gdprTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('userSettings.gdprHint') }}</p>
      <DownloadButton disabled>{{ t('userSettings.gdprExport') }}</DownloadButton>
    </NeutralContainer>

    <!-- Account deletion section -->
    <HelpSection :title="t('helpCenter.sessions.deleteTitle')">
      <p>{{ t('helpCenter.sessions.deleteText') }}</p>
    </HelpSection>

    <ErrorContainer class="space-y-3">
      <SubHeader>{{ t('userSettings.deleteTitle') }}</SubHeader>
      <p class="text-sm">{{ t('userSettings.deleteWarning') }}</p>
      <ErrorButton :icon="['fas', 'trash']" disabled>
        {{ t('userSettings.deleteAccount') }}
      </ErrorButton>
    </ErrorContainer>

    <HelpTip>{{ t('helpCenter.sessions.tip') }}</HelpTip>
  </HelpArticle>
</template>
