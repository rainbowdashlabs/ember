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
import InfoContainer from '@/components/container/InfoContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DummyNotificationMatrix from './notificationshelp/DummyNotificationMatrix.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.notifications.title')" :subtitle="t('helpCenter.notifications.subtitle')">
    <HelpSection :title="t('helpCenter.notifications.whatIs')">
      <p>{{ t('helpCenter.notifications.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.notifications.typesTitle')">
      <p>{{ t('helpCenter.notifications.typesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.notifications.matrixTitle')">
      <p>{{ t('helpCenter.notifications.matrixText') }}</p>
    </HelpSection>

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

    <DummyNotificationMatrix/>

    <HelpSection :title="t('helpCenter.notifications.feedsTitle')">
      <p>{{ t('helpCenter.notifications.feedsText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SubHeader>{{ t('userSettings.feedTitle') }}</SubHeader>
      <p class="text-sm text-(--text-muted)">{{ t('userSettings.feedHint') }}</p>

      <div class="space-y-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('userSettings.feedIcal') }}</FieldLabel>
          <div class="flex items-center gap-2">
            <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-xs break-all select-all">https://ember.beispiel.de/feed/ical/abc123</code>
            <SecondaryButton disabled>
              <font-awesome-icon :icon="['fas', 'copy']"/>
            </SecondaryButton>
          </div>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('userSettings.feedRss') }}</FieldLabel>
          <div class="flex items-center gap-2">
            <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-xs break-all select-all">https://ember.beispiel.de/feed/rss/abc123</code>
            <SecondaryButton disabled>
              <font-awesome-icon :icon="['fas', 'copy']"/>
            </SecondaryButton>
          </div>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('userSettings.feedAtom') }}</FieldLabel>
          <div class="flex items-center gap-2">
            <code class="flex-1 rounded bg-bg-light-accent dark:bg-bg-dark-accent px-3 py-2 text-xs break-all select-all">https://ember.beispiel.de/feed/atom/abc123</code>
            <SecondaryButton disabled>
              <font-awesome-icon :icon="['fas', 'copy']"/>
            </SecondaryButton>
          </div>
        </div>
      </div>

      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'rotate']" disabled>{{ t('userSettings.feedRegenerate') }}</SecondaryButton>
        <DeleteButton disabled/>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.notifications.guidesTitle')">
      <p>{{ t('helpCenter.notifications.guidesText') }}</p>
      <div class="flex gap-2 mt-3">
        <router-link :to="{name: 'help-profile-rss-feed'}">
          <SecondaryButton :icon="['fas', 'rss']">{{ t('helpCenter.notifications.rssFeedGuide') }}</SecondaryButton>
        </router-link>
        <router-link :to="{name: 'help-profile-ical-feed'}">
          <SecondaryButton :icon="['fas', 'calendar-days']">{{ t('helpCenter.notifications.icalGuide') }}</SecondaryButton>
        </router-link>
      </div>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.notifications.tip') }}</HelpTip>
  </HelpArticle>
</template>
