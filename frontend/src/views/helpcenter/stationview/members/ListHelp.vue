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
import TabBar from '@/components/navigation/TabBar.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import {ref} from 'vue'

const {t} = useI18n()

const activeTab = ref('ALL')
const tabs = [
  {key: 'ALL', label: t('membersList.tabAll')},
  {key: 'MEMBER', label: t('membersList.tabMember')},
  {key: 'MEMBER_MANAGER', label: t('membersList.tabMemberManager')},
  {key: 'TEAM', label: t('membersList.tabTeam')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersList.title')" :subtitle="t('helpCenter.membersList.subtitle')">
    <HelpSection :title="t('helpCenter.membersList.whatIs')">
      <p>{{ t('helpCenter.membersList.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersList.tabsTitle')">
      <p>{{ t('helpCenter.membersList.tabAll') }}</p>
      <p>{{ t('helpCenter.membersList.tabMember') }}</p>
      <p>{{ t('helpCenter.membersList.tabManager') }}</p>
      <p>{{ t('helpCenter.membersList.tabTeam') }}</p>
    </HelpSection>

    <!-- Dummy: Tabs -->
    <TabBar v-model="activeTab" :tabs="tabs"/>

    <!-- Dummy: Member table -->
    <NeutralContainer class="overflow-x-auto">
      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
            <th class="px-3 py-2 font-medium">{{ t('membersList.colName') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('membersList.colEmail') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('memberGroups.title') }}</th>
            <th class="px-3 py-2 font-medium">{{ t('helpCenter.exampleFields.phone') }}</th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
            <td class="px-3 py-2.5 font-medium">Max Mustermann</td>
            <td class="px-3 py-2.5">max@example.com</td>
            <td class="px-3 py-2.5"><PrimaryBadge>Anfänger</PrimaryBadge></td>
            <td class="px-3 py-2.5">0170 1234567</td>
          </tr>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
            <td class="px-3 py-2.5 font-medium">Anna Schmidt</td>
            <td class="px-3 py-2.5">anna@example.com</td>
            <td class="px-3 py-2.5"><SecondaryBadge>Fortgeschrittene</SecondaryBadge></td>
            <td class="px-3 py-2.5">0171 7654321</td>
          </tr>
          <tr class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
            <td class="px-3 py-2.5 font-medium">Lisa Weber</td>
            <td class="px-3 py-2.5">lisa@example.com</td>
            <td class="px-3 py-2.5"><PrimaryBadge>Betreuer</PrimaryBadge></td>
            <td class="px-3 py-2.5">0172 9876543</td>
          </tr>
        </tbody>
      </table>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersList.searchTitle')">
      <p>{{ t('helpCenter.membersList.searchText') }}</p>
      <p>{{ t('helpCenter.membersList.searchSave') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersList.columnsTitle')">
      <p>{{ t('helpCenter.membersList.columnsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersList.exportTitle')">
      <p>{{ t('helpCenter.membersList.exportText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersList.tip') }}</HelpTip>
  </HelpArticle>
</template>
