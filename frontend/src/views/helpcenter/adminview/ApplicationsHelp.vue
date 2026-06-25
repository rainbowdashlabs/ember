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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import ApplicationsTableDummy from './applicationshelp/ApplicationsTableDummy.vue'
import DenyModalDummy from './applicationshelp/DenyModalDummy.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.adminApplications.title')" :subtitle="t('helpCenter.adminApplications.subtitle')">
    <HelpSection :title="t('helpCenter.adminApplications.whatIs')">
      <p>{{ t('helpCenter.adminApplications.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApplications.processTitle')">
      <p>{{ t('helpCenter.adminApplications.accept') }}</p>
      <p>{{ t('helpCenter.adminApplications.deny') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApplications.tabsTitle')">
      <p>{{ t('helpCenter.adminApplications.tabsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApplications.denyModalTitle')">
      <p>{{ t('helpCenter.adminApplications.denyModalText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.adminApplications.exampleTitle')">
      <SectionHeader>{{ t('adminApplications.title') }}</SectionHeader>
      <TabBar model-value="pending" :tabs="[{key: 'pending', label: t('adminApplications.pending')}, {key: 'all', label: t('adminApplications.all')}]" class="mb-4" />
      <ApplicationsTableDummy />
      <DenyModalDummy />
    </HelpSection>

    <HelpTip>{{ t('helpCenter.adminApplications.tip') }}</HelpTip>
  </HelpArticle>
</template>
