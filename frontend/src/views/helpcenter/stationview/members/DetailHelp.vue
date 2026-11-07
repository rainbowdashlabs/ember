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
import ButtonRow from '@/components/button/ButtonRow.vue'
import MemberLinkBadge from '@/components/accountlink/MemberLinkBadge.vue'
import InventorySection from '@/views/stationview/members/detailview/InventorySection.vue'
import ScanHandOutPanel from '@/views/stationview/members/detailview/inventorytab/ScanHandOutPanel.vue'
import type {HandOutMode} from '@/components/inventory/HandOutChoice.vue'
import {LinkStatus} from '@/api/generated/schema'
import {ref} from 'vue'
import {items, requirements} from './detailhelp/inventoryFixtures'

const {t} = useI18n()

const activeTab = ref('profile')
const scanValue = ref('')
const handOutMode = ref<HandOutMode>('NOW')
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

    <HelpSection :title="t('helpCenter.membersDetail.headerTitle')">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']">
          {{ t('memberDetail.back') }}
        </SecondaryButton>
        <ButtonRow align="end">
          <ErrorButton :icon="['fas', 'user-slash']">
            {{ t('memberDetail.markFormer') }}
          </ErrorButton>
          <ErrorButton :icon="['fas', 'trash']">
            {{ t('memberDetail.deleteData') }}
          </ErrorButton>
          <PrimaryButton :icon="['fas', 'pen']">
            {{ t('memberDetail.edit') }}
          </PrimaryButton>
        </ButtonRow>
      </div>

      <SectionHeader class="mt-3">{{ t('helpCenter.sample.people.maxMustermann') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">max@example.com</p>

      <TabBar :model-value="activeTab" :tabs="tabs" class="mt-3"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.fieldsTitle')">
      <p>{{ t('helpCenter.membersDetail.fieldsText') }}</p>
    </HelpSection>

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
            <span class="ml-1 font-medium">{{ t('helpCenter.sample.people.petraMustermann') }}</span>
          </div>
        </div>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.permissionsTitle')">
      <p>{{ t('helpCenter.membersDetail.permissionsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.permissionsExampleTitle')">
      <NeutralContainer class="space-y-3">
        <SubHeader class="text-sm">{{ t('memberDetail.userType') }}</SubHeader>
        <span class="text-sm font-medium">{{ t('memberEdit.userTypeMember') }}</span>
      </NeutralContainer>
      <NeutralContainer class="space-y-3 mt-2">
        <SubHeader class="text-sm">{{ t('memberDetail.permissions') }}</SubHeader>
        <div class="flex flex-wrap gap-2">
          <PrimaryBadge>{{ t('permissions.ATTENDANCE_MANAGER.label') }}</PrimaryBadge>
        </div>
      </NeutralContainer>
      <NeutralContainer class="space-y-3 mt-2">
        <SubHeader class="text-sm">{{ t('memberDetail.groups') }}</SubHeader>
        <div class="flex flex-wrap gap-2">
          <SecondaryBadge>{{ t('helpCenter.sample.groups.beginners') }}</SecondaryBadge>
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

    <HelpSection :title="t('helpCenter.membersDetail.scanTitle')">
      <p>{{ t('helpCenter.membersDetail.scanText') }}</p>
      <ScanHandOutPanel
          v-model:hand-out-mode="handOutMode"
          v-model:scan-value="scanValue"
          :scan-busy="false"
          :scan-failure="null"
          scan-success=""
      />
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.inventoryExampleTitle')">
      <p>{{ t('helpCenter.membersDetail.inventoryExampleText') }}</p>
      <InventorySection :member-inventory="items" :requirements="requirements" can-assign can-manage-inventory/>
      <p>{{ t('helpCenter.membersDetail.returnEverythingText') }}</p>
      <p>{{ t('helpCenter.membersDetail.movementsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.changesTitle')">
      <p>{{ t('helpCenter.membersDetail.changesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.formerTitle')">
      <p>{{ t('helpCenter.membersDetail.formerText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersDetail.linkTitle')">
      <p>{{ t('helpCenter.membersDetail.linkText') }}</p>
      <NeutralContainer class="space-y-3">
        <div class="flex flex-wrap items-center gap-2">
          <SubHeader>{{ t('memberLinks.title') }}</SubHeader>
          <MemberLinkBadge :status="LinkStatus.WAITING"/>
          <MemberLinkBadge :status="LinkStatus.DECLINED"/>
          <MemberLinkBadge :status="LinkStatus.EXPIRED"/>
        </div>
        <ButtonRow>
          <SecondaryButton :icon="['fas', 'paper-plane']">{{ t('memberLinks.sendAgain') }}</SecondaryButton>
        </ButtonRow>
      </NeutralContainer>
      <p>{{ t('helpCenter.membersDetail.linkSendAgainText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersDetail.tip') }}</HelpTip>
  </HelpArticle>
</template>
