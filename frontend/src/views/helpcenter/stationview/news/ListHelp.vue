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
import HelpRoleToggle from '@/components/helpcenter/HelpRoleToggle.vue'
import type {HelpPerspective} from '@/components/helpcenter/HelpRoleToggle.vue'
import {StationPermission} from '@/api/types'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import DummyNewsList from './listhelp/DummyNewsList.vue'

const {t} = useI18n()

const perspectives: HelpPerspective[] = [
  {key: 'member', label: t('helpCenter.roles.member'), permissions: [StationPermission.USER]},
  {key: 'manager', label: t('helpCenter.roles.manager'), permissions: [StationPermission.NEWS_MANAGER]},
]
const activeView = ref('')
</script>

<template>
  <HelpArticle :title="t('helpCenter.news.title')" :subtitle="t('helpCenter.news.subtitle')">
    <HelpSection :title="t('helpCenter.news.whatIs')">
      <p>{{ t('helpCenter.news.whatIsText') }}</p>
    </HelpSection>

    <HelpRoleToggle v-model="activeView" :perspectives="perspectives"/>

    <HelpSection :title="t('helpCenter.news.reading')">
      <p>{{ t('helpCenter.news.readingText') }}</p>
      <p>{{ t('helpCenter.news.comments') }}</p>
    </HelpSection>

    <!-- Dummy: News list -->
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('news.title') }}</SectionHeader>
      <PrimaryButton :icon="['fas', 'plus']" v-if="activeView === 'manager'">
        {{ t('news.create') }}
      </PrimaryButton>
    </div>

    <DummyNewsList :active-view="activeView"/>

    <HelpSection :title="t('helpCenter.newsList.detailTitle')">
      <p>{{ t('helpCenter.newsList.detailText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.newsList.federatedTitle')">
      <p>{{ t('helpCenter.newsList.federatedText') }}</p>
    </HelpSection>

    <template v-if="activeView === 'manager'">
      <HelpSection :title="t('helpCenter.news.managerTitle')">
        <p>{{ t('helpCenter.news.managerText') }}</p>
        <p>{{ t('helpCenter.news.managerCreate') }}</p>
        <p>{{ t('helpCenter.news.managerVisibility') }}</p>
        <p>{{ t('helpCenter.news.managerEditDelete') }}</p>
        <p>{{ t('helpCenter.news.managerModerate') }}</p>
      </HelpSection>
    </template>

    <HelpTip>{{ t('helpCenter.news.tip') }}</HelpTip>
  </HelpArticle>
</template>
