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

const {t} = useI18n()

const dummyEmailEnabled = ref(true)
const dummyNotifyNews = ref(true)
const dummyNotifyEvents = ref(false)
const dummyNotifyEventStatus = ref(true)
</script>

<template>
  <HelpArticle :title="t('helpCenter.settings.title')" :subtitle="t('helpCenter.settings.subtitle')">
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
      <h3 class="font-semibold text-sm">{{ t('userSettings.emailTitle') }}</h3>

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
        <h3 class="font-semibold text-sm pt-2">{{ t('userSettings.notifications') }}</h3>
        <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

        <div class="space-y-3">
          <div class="flex items-center justify-between">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyNews') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyNewsHint') }}</p>
            </div>
            <ToggleInput v-model="dummyNotifyNews"/>
          </div>

          <div class="flex items-center justify-between">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyNewEvents') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyNewEventsHint') }}</p>
            </div>
            <ToggleInput v-model="dummyNotifyEvents"/>
          </div>

          <div class="flex items-center justify-between">
            <div>
              <span class="text-sm font-medium">{{ t('userSettings.notifyEventStatus') }}</span>
              <p class="text-xs text-(--text-muted)">{{ t('userSettings.notifyEventStatusHint') }}</p>
            </div>
            <ToggleInput v-model="dummyNotifyEventStatus"/>
          </div>
        </div>
      </template>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.settings.typesTitle')">
      <p>{{ t('helpCenter.settings.typesText') }}</p>
      <p>{{ t('helpCenter.settings.typeNews') }}</p>
      <p>{{ t('helpCenter.settings.typeEvents') }}</p>
      <p>{{ t('helpCenter.settings.typeEventChanges') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.settings.tip') }}</HelpTip>
  </HelpArticle>
</template>
