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
import TabBar from '@/components/navigation/TabBar.vue'
import DummyMemberCard from './changeshelp/DummyMemberCard.vue'

const {t} = useI18n()

const activeTab = ref('pending')
const tabs = [
  {key: 'pending', label: t('memberChanges.tabPending')},
  {key: 'history', label: t('memberChanges.tabHistory')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersChanges.title')" :subtitle="t('helpCenter.membersChanges.subtitle')">
    <HelpSection :title="t('helpCenter.membersChanges.whatIs')">
      <p>{{ t('helpCenter.membersChanges.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.tabsTitle')">
      <p>{{ t('helpCenter.membersChanges.tabsText') }}</p>
      <TabBar :model-value="activeTab" :tabs="tabs" class="mt-2"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.confirmTitle')">
      <p>{{ t('helpCenter.membersChanges.confirmText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.exampleTitle')">
      <div class="space-y-3">
        <DummyMemberCard/>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.historyTitle')">
      <p>{{ t('helpCenter.membersChanges.historyText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersChanges.badgeTitle')">
      <p>{{ t('helpCenter.membersChanges.badgeText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersChanges.tip') }}</HelpTip>
  </HelpArticle>
</template>
