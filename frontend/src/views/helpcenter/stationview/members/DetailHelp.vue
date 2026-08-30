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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import IconButton from '@/components/button/IconButton.vue'
import {ref} from 'vue'

const {t} = useI18n()

const activeTab = ref('profile')
const tabs = [
  {key: 'profile', label: t('memberDetail.tabProfile')},
  {key: 'permissions', label: t('memberDetail.tabPermissions')},
  {key: 'guardians', label: t('memberDetail.tabGuardians')},
  {key: 'absences', label: t('memberDetail.tabAbsences')},
  {key: 'inventory', label: t('memberDetail.tabInventory')},
  {key: 'notes', label: t('memberDetail.tabNotes')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersDetail.title')" :subtitle="t('helpCenter.membersDetail.subtitle')">
    <HelpSection :title="t('helpCenter.membersDetail.whatShown')">
      <p>{{ t('helpCenter.membersDetail.whatShownText') }}</p>
    </HelpSection>

    <!-- Dummy: Member header with action buttons -->
    <HelpSection :title="t('helpCenter.membersDetail.headerTitle')">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']">
          {{ t('memberDetail.back') }}
        </SecondaryButton>
        <div class="flex items-center gap-2">
          <ErrorButton :icon="['fas', 'user-slash']">
            {{ t('memberDetail.markFormer') }}
          </ErrorButton>
          <ErrorButton :icon="['fas', 'trash']">
            {{ t('memberDetail.deleteData') }}
          </ErrorButton>
          <PrimaryButton :icon="['fas', 'pen']">
            {{ t('memberDetail.edit') }}
          </PrimaryButton>
        </div>
      </div>

      <SectionHeader class="mt-3">Max Mustermann</SectionHeader>
      <p class="text-sm text-(--text-muted)">max@example.com</p>

      <!-- Dummy: Tab bar showing all tabs -->
      <TabBar :model-value="activeTab" :tabs="tabs" class="mt-3"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.fieldsTitle')">
      <p>{{ t('helpCenter.membersDetail.fieldsText') }}</p>
    </HelpSection>

    <!-- Dummy: Profile fields -->
    <HelpSection :title="t('helpCenter.membersDetail.profileExampleTitle')">
      <NeutralContainer class="space-y-3">
        <SubHeader class="text-sm">{{ t('memberDetail.fields') }}</SubHeader>
        <div class="grid gap-2 sm:grid-cols-2">
          <div class="text-sm">
            <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.phone') }}:</span>
            <span class="ml-1 font-medium">0170 1234567</span>
          </div>
          <div class="text-sm">
            <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.birthday') }}:</span>
            <span class="ml-1 font-medium">15.03.2010</span>
          </div>
          <div class="text-sm">
            <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.clothingSize') }}:</span>
            <span class="ml-1 font-medium">M</span>
          </div>
          <div class="text-sm">
            <span class="text-(--text-muted)">{{ t('helpCenter.exampleFields.emergencyContact') }}:</span>
            <span class="ml-1 font-medium">Petra Mustermann</span>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.permissionsTitle')">
      <p>{{ t('helpCenter.membersDetail.permissionsText') }}</p>
    </HelpSection>

    <!-- Dummy: Permissions tab -->
    <HelpSection :title="t('helpCenter.membersDetail.permissionsExampleTitle')">
      <NeutralContainer class="space-y-3">
        <SubHeader class="text-sm">{{ t('memberDetail.userType') }}</SubHeader>
        <span class="text-sm font-medium">{{ t('memberEdit.userTypeMember') }}</span>
      </NeutralContainer>
      <NeutralContainer class="space-y-3 mt-2">
        <SubHeader class="text-sm">{{ t('memberDetail.permissions') }}</SubHeader>
        <div class="flex flex-wrap gap-2">
          <PrimaryBadge>{{ t('helpCenter.basics.permissions.mgmt.attendanceManagement.name') }}</PrimaryBadge>
        </div>
      </NeutralContainer>
      <NeutralContainer class="space-y-3 mt-2">
        <SubHeader class="text-sm">{{ t('memberDetail.groups') }}</SubHeader>
        <div class="flex flex-wrap gap-2">
          <SecondaryBadge>Anfänger</SecondaryBadge>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.managersTitle')">
      <p>{{ t('helpCenter.membersDetail.managersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.absencesTitle')">
      <p>{{ t('helpCenter.membersDetail.absencesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.inventoryTitle')">
      <p>{{ t('helpCenter.membersDetail.inventoryText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.missingTitle')">
      <p>{{ t('helpCenter.membersDetail.missingText') }}</p>
      <p>{{ t('helpCenter.membersDetail.missingHandOutText') }}</p>
    </HelpSection>

    <!-- Dummy: Inventory items -->
    <HelpSection :title="t('helpCenter.membersDetail.inventoryExampleTitle')">
      <NeutralContainer class="space-y-3">
        <div class="flex items-center justify-between">
          <SubHeader>{{ t('memberDetail.inventory') }}</SubHeader>
          <PrimaryButton :icon="['fas', 'plus']" disabled>
            {{ t('memberDetail.assignItem') }}
          </PrimaryButton>
        </div>
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
          <NeutralContainer>
            <div class="flex items-start justify-between gap-2">
              <div>
                <div class="font-medium text-sm">Helm <span class="font-normal text-(--text-muted)">M</span></div>
                <div class="text-xs text-(--text-muted)">Helme</div>
              </div>
              <div class="flex items-center gap-1">
                <IconButton :icon="['fas', 'rotate']" label="Tausch" disabled/>
                <IconButton :icon="['fas', 'arrow-right-arrow-left']" label="Umzuweisen" disabled/>
                <IconButton :icon="['fas', 'xmark']" label="Aufheben" disabled/>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.changesTitle')">
      <p>{{ t('helpCenter.membersDetail.changesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.formerTitle')">
      <p>{{ t('helpCenter.membersDetail.formerText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
