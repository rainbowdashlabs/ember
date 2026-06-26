/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DummySessionItem from './DummySessionItem.vue'

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('userSettings.sessions') }}</SubHeader>
      <ErrorButton :icon="['fas', 'trash']" disabled>
        {{ t('userSettings.invalidateAll') }}
      </ErrorButton>
    </div>

    <div class="space-y-2">
      <DummySessionItem
        :os-icon="['fab', 'linux']"
        :browser-icon="['fab', 'firefox-browser']"
        browser-name="Firefox"
        os-name="Linux"
        :last-active="t('userSettings.justNow')"
        created="07.06.2026, 08:15"
        location="Berlin"
        current
      />
      <DummySessionItem
        :os-icon="['fab', 'apple']"
        :browser-icon="['fab', 'safari']"
        browser-name="Safari"
        os-name="macOS"
        :last-active="t('userSettings.hoursAgo', {n: 3})"
        created="05.06.2026, 14:30"
        location="Hamburg"
      />
      <DummySessionItem
        :os-icon="['fab', 'android']"
        :browser-icon="['fab', 'chrome']"
        browser-name="Chrome"
        os-name="Android"
        :last-active="t('userSettings.daysAgo', {n: 2})"
        created="01.06.2026, 09:00"
        :location="t('userSettings.unknownLocation')"
      />
    </div>
  </NeutralContainer>
</template>
