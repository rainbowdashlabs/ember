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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'

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

    <!-- Dummy: Mail provider info -->
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

    <!-- Dummy: Notification matrix table -->
    <NeutralContainer class="space-y-4">
      <SubHeader class="text-sm">{{ t('userSettings.notifications') }}</SubHeader>
      <p class="text-xs text-(--text-muted)">{{ t('userSettings.notificationsHint') }}</p>

      <!-- Header row -->
      <div class="grid grid-cols-[1fr_auto_auto_auto] gap-x-4 items-center text-xs font-semibold text-(--text-muted) border-b border-(--border) pb-2">
        <span></span>
        <span class="w-12 text-center">{{ t('userSettings.columnApp') }}</span>
        <span class="w-12 text-center">{{ t('userSettings.columnEmail') }}</span>
        <span class="w-12 text-center">{{ t('userSettings.columnFeed') }}</span>
      </div>

      <!-- Notification rows -->
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
    </NeutralContainer>

    <!-- Dummy: Feed token section -->
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
