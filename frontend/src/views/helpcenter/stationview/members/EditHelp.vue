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
import TabBar from '@/components/navigation/TabBar.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {ref} from 'vue'

const {t} = useI18n()

const activeTab = ref('profile')
const tabs = [
  {key: 'profile', label: t('memberEdit.tabProfile')},
  {key: 'permissions', label: t('memberEdit.tabPermissions')},
  {key: 'relations', label: t('memberEdit.tabRelations')},
  {key: 'notes', label: t('memberEdit.tabNotes')},
]

const dummyUserType = ref('MEMBER')
const dummyRoles = [
  {id: 1, permission: 'ATTENDANCE_MANAGEMENT', label: 'Anwesenheitsverwaltung'},
  {id: 2, permission: 'INVENTORY_READ', label: 'Inventar lesen'},
]
const dummySelected = new Set([1])
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersEdit.title')" :subtitle="t('helpCenter.membersEdit.subtitle')">
    <HelpSection :title="t('helpCenter.membersEdit.whatShown')">
      <p>{{ t('helpCenter.membersEdit.whatShownText') }}</p>
    </HelpSection>

    <!-- Dummy: Back button + header -->
    <HelpSection :title="t('helpCenter.membersEdit.exampleTitle')">
      <SecondaryButton :icon="['fas', 'chevron-left']">
        {{ t('memberEdit.back') }}
      </SecondaryButton>
      <SectionHeader class="mt-3">Max Mustermann</SectionHeader>

      <!-- Dummy: Tab bar with correct 4 tabs -->
      <TabBar :model-value="activeTab" :tabs="tabs" class="mt-3"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.accountTitle')">
      <p>{{ t('helpCenter.membersEdit.accountText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.fieldsTitle')">
      <p>{{ t('helpCenter.membersEdit.fieldsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.permissionsTitle')">
      <p>{{ t('helpCenter.membersEdit.permissionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Permissions tab content -->
    <HelpSection :title="t('helpCenter.membersEdit.permissionsExampleTitle')">
      <NeutralContainer class="space-y-4">
        <div class="space-y-2">
          <SubHeader>{{ t('memberEdit.userType') }}</SubHeader>
          <SelectInput :model-value="dummyUserType" class="w-full sm:w-64">
            <option value="MANAGER">{{ t('memberEdit.userTypeManager') }}</option>
            <option value="TEAM">{{ t('memberEdit.userTypeTeam') }}</option>
            <option value="GUARDIAN">{{ t('memberEdit.userTypeGuardian') }}</option>
            <option value="MEMBER">{{ t('memberEdit.userTypeMember') }}</option>
            <option value="TRIAL">{{ t('memberEdit.userTypeTrial') }}</option>
          </SelectInput>
        </div>

        <div class="space-y-2">
          <SubHeader>{{ t('memberEdit.permissions') }}</SubHeader>
          <PermissionPicker :model-value="dummySelected" :all-roles="dummyRoles"/>
        </div>

        <div class="space-y-2">
          <SubHeader>{{ t('memberGroups.title') }}</SubHeader>
          <div class="flex flex-wrap gap-2">
            <SecondaryBadge>Anfänger</SecondaryBadge>
            <SecondaryBadge class="opacity-50">Fortgeschrittene</SecondaryBadge>
          </div>
        </div>

        <div class="space-y-2">
          <SubHeader>{{ t('userTags.title') }}</SubHeader>
          <div class="flex flex-wrap gap-2">
            <InfoBadge>Ersthelfer</InfoBadge>
            <InfoBadge class="opacity-50">Fahrer</InfoBadge>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.relationsTitle')">
      <p>{{ t('helpCenter.membersEdit.relationsText') }}</p>
    </HelpSection>

    <!-- Dummy: Relations tab content -->
    <HelpSection :title="t('helpCenter.membersEdit.relationsExampleTitle')">
      <NeutralContainer class="space-y-3">
        <SubHeader>{{ t('memberEdit.guardians') }}</SubHeader>
        <div class="rounded-lg px-3 py-2 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30 flex items-center justify-between">
          <span class="text-sm font-medium">Petra Mustermann</span>
          <MutedText size="sm">petra@example.com</MutedText>
        </div>
        <SecondaryButton :icon="['fas', 'plus']">
          {{ t('memberEdit.addGuardian') }}
        </SecondaryButton>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.notesTitle')">
      <p>{{ t('helpCenter.membersEdit.notesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersEdit.formerTitle')">
      <p>{{ t('helpCenter.membersEdit.formerText') }}</p>
      <ErrorButton :icon="['fas', 'user-slash']" class="mt-2">
        {{ t('memberDetail.markFormer') }}
      </ErrorButton>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
