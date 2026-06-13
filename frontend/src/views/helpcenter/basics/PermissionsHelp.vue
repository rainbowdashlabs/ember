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
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import BulletList from '@/components/typography/BulletList.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

const userTypes = [
  {key: 'member', badge: PrimaryBadge, icon: ['fas', 'user']},
  {key: 'guardian', badge: SecondaryBadge, icon: ['fas', 'users']},
  {key: 'team', badge: InfoBadge, icon: ['fas', 'people-group']},
  {key: 'manager', badge: SuccessBadge, icon: ['fas', 'user-gear']},
  {key: 'admin', badge: ErrorBadge, icon: ['fas', 'shield']},
]

const managementPermissions = [
  'attendanceManagement',
  'inventoryManagement',
  'eventManagement',
  'memberManagement',
  'newsManagement',
  'pollManagement',
  'lostAndFoundManagement',
  'attendanceExport',
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.basics.permissions.title')" :subtitle="t('helpCenter.basics.permissions.subtitle')">
    <HelpSection :title="t('helpCenter.basics.permissions.whatAre')">
      <p>{{ t('helpCenter.basics.permissions.whatAreText') }}</p>
      <p>{{ t('helpCenter.basics.permissions.whatAreText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.permissions.overview')">
      <div class="space-y-3">
        <NeutralContainer v-for="ut in userTypes" :key="ut.key" class="flex items-start gap-3 p-4">
          <font-awesome-icon :icon="ut.icon" class="h-5 w-5 text-primary mt-0.5 shrink-0"/>
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-1">
              <component :is="ut.badge">{{ t(`helpCenter.basics.permissions.userType.${ut.key}.name`) }}</component>
            </div>
            <p class="text-sm">{{ t(`helpCenter.basics.permissions.userType.${ut.key}.desc`) }}</p>
            <MutedText tag="p" class="mt-1">{{ t(`helpCenter.basics.permissions.userType.${ut.key}.example`) }}</MutedText>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.permissions.hierarchy')">
      <p>{{ t('helpCenter.basics.permissions.hierarchyText') }}</p>
      <NeutralContainer class="p-4 mt-3">
        <div class="flex flex-col items-center gap-1 text-sm">
          <ErrorBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.permissions.userType.admin.name') }}</ErrorBadge>
          <font-awesome-icon :icon="['fas', 'arrow-down']" class="text-(--text-muted)"/>
          <SuccessBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.permissions.userType.manager.name') }}</SuccessBadge>
          <font-awesome-icon :icon="['fas', 'arrow-down']" class="text-(--text-muted)"/>
          <InfoBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.permissions.userType.team.name') }}</InfoBadge>
          <font-awesome-icon :icon="['fas', 'arrow-down']" class="text-(--text-muted)"/>
          <div class="flex gap-4">
            <PrimaryBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.permissions.userType.member.name') }}</PrimaryBadge>
            <SecondaryBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.permissions.userType.guardian.name') }}</SecondaryBadge>
          </div>
        </div>
      </NeutralContainer>
      <p class="mt-3">{{ t('helpCenter.basics.permissions.hierarchyText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.permissions.management')">
      <p>{{ t('helpCenter.basics.permissions.managementText') }}</p>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3">
        <NeutralContainer v-for="mp in managementPermissions" :key="mp" class="p-3">
          <p class="font-semibold text-sm">{{ t(`helpCenter.basics.permissions.mgmt.${mp}.name`) }}</p>
          <p class="text-xs text-(--text-muted)">{{ t(`helpCenter.basics.permissions.mgmt.${mp}.desc`) }}</p>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.permissions.groups')">
      <p>{{ t('helpCenter.basics.permissions.groupsText') }}</p>
      <p>{{ t('helpCenter.basics.permissions.groupsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.permissions.owner')">
      <p>{{ t('helpCenter.basics.permissions.ownerText') }}</p>
      <BulletList>
        <li>{{ t('helpCenter.basics.permissions.owner1') }}</li>
        <li>{{ t('helpCenter.basics.permissions.owner2') }}</li>
        <li>{{ t('helpCenter.basics.permissions.owner3') }}</li>
      </BulletList>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.permissions.tip') }}</HelpTip>
  </HelpArticle>
</template>
