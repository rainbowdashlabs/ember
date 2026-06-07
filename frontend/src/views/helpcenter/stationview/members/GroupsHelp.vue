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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import IconButton from '@/components/button/IconButton.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {ref} from 'vue'

const {t} = useI18n()

const detailTab = ref('members')
const detailTabs = [
  {key: 'members', label: t('memberGroups.tabMembers')},
  {key: 'permissions', label: t('memberGroups.tabPermissions')},
]

const dummyRoles = [
  {id: 1, permission: 'ATTENDANCE_MANAGEMENT', label: 'Anwesenheitsverwaltung'},
  {id: 2, permission: 'INVENTORY_READ', label: 'Inventar lesen'},
]
const dummySelected = new Set([1])
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersGroups.title')" :subtitle="t('helpCenter.membersGroups.subtitle')">
    <HelpSection :title="t('helpCenter.membersGroups.whatIs')">
      <p>{{ t('helpCenter.membersGroups.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.createTitle')">
      <p>{{ t('helpCenter.membersGroups.create') }}</p>
      <p>{{ t('helpCenter.membersGroups.select') }}</p>
      <p>{{ t('helpCenter.membersGroups.addMember') }}</p>
    </HelpSection>

    <!-- Dummy: Groups list + panel -->
    <HelpSection :title="t('helpCenter.membersGroups.exampleTitle')">
      <div class="grid gap-6 lg:grid-cols-2">
        <!-- Groups list -->
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>{{ t('memberGroups.title') }}</SectionHeader>
            <PrimaryButton :icon="['fas', 'plus']">
              {{ t('memberGroups.create') }}
            </PrimaryButton>
          </div>
          <div class="space-y-2">
            <NeutralContainer class="flex items-center justify-between gap-2 flex-wrap cursor-pointer border-primary">
              <span class="flex items-center gap-2">
                <span class="inline-block h-3 w-3 rounded-full" style="background-color: #3694FF"></span>
                <span class="font-medium">Anfänger</span>
              </span>
              <div class="flex items-center gap-2">
                <IconButton :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')" class="text-(--text-muted) hover:text-primary"/>
                <EditButton/>
                <DeleteButton/>
              </div>
            </NeutralContainer>
            <NeutralContainer class="flex items-center justify-between gap-2 flex-wrap cursor-pointer hover:border-primary">
              <span class="flex items-center gap-2">
                <span class="inline-block h-3 w-3 rounded-full" style="background-color: #00C507"></span>
                <span class="font-medium">Fortgeschrittene</span>
              </span>
              <div class="flex items-center gap-2">
                <IconButton :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')" class="text-(--text-muted) hover:text-primary"/>
                <EditButton/>
                <DeleteButton/>
              </div>
            </NeutralContainer>
          </div>
        </div>

        <!-- Group detail panel -->
        <div class="space-y-4">
          <SectionHeader>Anfänger</SectionHeader>

          <!-- Tab bar for members/permissions -->
          <TabBar :model-value="detailTab" :tabs="detailTabs"/>

          <!-- Members tab content -->
          <div class="space-y-1">
            <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.currentMembers') }}</FieldLabel>
            <div class="space-y-1">
              <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
                <span class="text-sm font-medium">Max Mustermann</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-error hover:text-error/80" />
              </div>
              <div class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
                <span class="text-sm font-medium">Anna Schmidt</span>
                <IconButton :icon="['fas', 'xmark']" label="Remove" class="text-error hover:text-error/80" />
              </div>
            </div>
          </div>
          <div class="space-y-1">
            <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.addMembers') }}</FieldLabel>
            <div class="space-y-1">
              <div class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer">
                <span class="text-sm font-medium">Lisa Weber</span>
                <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
              </div>
            </div>
          </div>
        </div>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.rolesTitle')">
      <p>{{ t('helpCenter.membersGroups.rolesText') }}</p>
    </HelpSection>

    <!-- Dummy: Group permissions tab with PermissionPicker -->
    <HelpSection :title="t('helpCenter.membersGroups.permissionsExampleTitle')">
      <NeutralContainer class="space-y-2">
        <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.tabPermissions') }}</FieldLabel>
        <PermissionPicker :model-value="dummySelected" :all-roles="dummyRoles"/>
        <MutedText size="sm">{{ t('helpCenter.membersGroups.permissionsHint') }}</MutedText>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.colorTitle')">
      <p>{{ t('helpCenter.membersGroups.colorText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersGroups.convertTitle')">
      <p>{{ t('helpCenter.membersGroups.convertText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersGroups.tip') }}</HelpTip>
  </HelpArticle>
</template>
