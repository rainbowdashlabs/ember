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
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const {t} = useI18n()

const activeTab = ref('ALL')
const tabs = [
  {key: 'ALL', label: t('membersList.tabAll')},
  {key: 'MEMBER', label: t('membersList.tabMember')},
  {key: 'GUARDIAN', label: t('membersList.tabMemberManager')},
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
          <THead>
            <Th>{{ t('membersList.colName') }}</Th>
            <Th>{{ t('membersList.colEmail') }}</Th>
            <Th>{{ t('memberGroups.title') }}</Th>
            <Th>{{ t('helpCenter.exampleFields.phone') }}</Th>
          </THead>
        </thead>
        <tbody>
          <TRow class="cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
            <Td class="font-medium">Max Mustermann</Td>
            <Td>max@example.com</Td>
            <Td><PrimaryBadge>Anfänger</PrimaryBadge></Td>
            <Td>0170 1234567</Td>
          </TRow>
          <TRow class="cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
            <Td class="font-medium">Anna Schmidt</Td>
            <Td>anna@example.com</Td>
            <Td><SecondaryBadge>Fortgeschrittene</SecondaryBadge></Td>
            <Td>0171 7654321</Td>
          </TRow>
          <TRow class="cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
            <Td class="font-medium">Lisa Weber</Td>
            <Td>lisa@example.com</Td>
            <Td><PrimaryBadge>Betreuer</PrimaryBadge></Td>
            <Td>0172 9876543</Td>
          </TRow>
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
