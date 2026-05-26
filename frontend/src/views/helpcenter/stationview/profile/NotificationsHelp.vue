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
import SecondaryButton from '@/components/button/SecondaryButton.vue'

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

    <HelpSection :title="t('helpCenter.notifications.feedsTitle')">
      <p>{{ t('helpCenter.notifications.feedsText') }}</p>
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
