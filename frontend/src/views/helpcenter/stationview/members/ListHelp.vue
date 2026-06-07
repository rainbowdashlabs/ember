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
import HelpPermissionGuard from '@/components/helpcenter/HelpPermissionGuard.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import {ref} from 'vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import {StationPermission} from '@/api/types'

const {t} = useI18n()

const activeTab = ref('ALL')
const tabs = [
  {key: 'ALL', label: t('membersList.tabAll')},
  {key: 'TRIAL', label: t('membersList.tabTrial')},
  {key: 'MEMBER', label: t('membersList.tabMember')},
  {key: 'GUARDIAN', label: t('membersList.tabMemberManager')},
  {key: 'TEAM', label: t('membersList.tabTeam')},
  {key: 'MANAGER', label: t('membersList.tabManager')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersList.title')" :subtitle="t('helpCenter.membersList.subtitle')">
    <HelpSection :title="t('helpCenter.membersList.whatIs')">
      <p>{{ t('helpCenter.membersList.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersList.tabsTitle')">
      <p>{{ t('helpCenter.membersList.tabAll') }}</p>
      <p>{{ t('helpCenter.membersList.tabTrial') }}</p>
      <p>{{ t('helpCenter.membersList.tabMember') }}</p>
      <p>{{ t('helpCenter.membersList.tabManager') }}</p>
      <p>{{ t('helpCenter.membersList.tabTeam') }}</p>
      <p>{{ t('helpCenter.membersList.tabManagerRole') }}</p>
    </HelpSection>

    <!-- Dummy: Tabs with all 6 options -->
    <HelpSection :title="t('helpCenter.membersList.exampleTitle')">
      <TabBar :model-value="activeTab" :tabs="tabs"/>

      <!-- Dummy: Member table with user type column -->
      <NeutralContainer class="overflow-x-auto mt-3">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th>{{ t('membersList.colName') }}</Th>
              <Th>{{ t('membersList.colEmail') }}</Th>
              <Th>{{ t('membersList.colUserType') }}</Th>
              <Th>{{ t('memberGroups.title') }}</Th>
              <Th>{{ t('helpCenter.exampleFields.phone') }}</Th>
            </THead>
          </thead>
          <tbody>
            <TRow class="cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
              <Td class="font-medium">Max Mustermann</Td>
              <Td>max@example.com</Td>
              <Td><InfoBadge>{{ t('memberEdit.userTypeMember') }}</InfoBadge></Td>
              <Td><PrimaryBadge>Anfänger</PrimaryBadge></Td>
              <Td>0170 1234567</Td>
            </TRow>
            <TRow class="cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
              <Td class="font-medium">Anna Schmidt</Td>
              <Td>anna@example.com</Td>
              <Td><InfoBadge>{{ t('memberEdit.userTypeTeam') }}</InfoBadge></Td>
              <Td><SecondaryBadge>Fortgeschrittene</SecondaryBadge></Td>
              <Td>0171 7654321</Td>
            </TRow>
            <TRow class="cursor-pointer hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30">
              <Td class="font-medium">Lisa Weber</Td>
              <Td>lisa@example.com</Td>
              <Td><InfoBadge>{{ t('memberEdit.userTypeGuardian') }}</InfoBadge></Td>
              <Td><PrimaryBadge>Betreuer</PrimaryBadge></Td>
              <Td>0172 9876543</Td>
            </TRow>
          </tbody>
        </table>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersList.searchTitle')">
      <p>{{ t('helpCenter.membersList.searchText') }}</p>
      <HelpPermissionGuard :permissions="[StationPermission.MEMBER_EDIT]" :label="t('helpCenter.permissionLabel.memberEdit')">
        <p>{{ t('helpCenter.membersList.searchSave') }}</p>
      </HelpPermissionGuard>
    </HelpSection>

    <HelpPermissionGuard :permissions="[StationPermission.MEMBER_EDIT]" :label="t('helpCenter.permissionLabel.memberEdit')">
      <HelpSection :title="t('helpCenter.membersList.columnsTitle')">
        <p>{{ t('helpCenter.membersList.columnsText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpPermissionGuard :permissions="[StationPermission.MEMBER_EXPORT]" :label="t('helpCenter.permissionLabel.memberExport')">
      <HelpSection :title="t('helpCenter.membersList.exportTitle')">
        <p>{{ t('helpCenter.membersList.exportText') }}</p>
      </HelpSection>
    </HelpPermissionGuard>

    <HelpTip>{{ t('helpCenter.membersList.tip') }}</HelpTip>
  </HelpArticle>
</template>
