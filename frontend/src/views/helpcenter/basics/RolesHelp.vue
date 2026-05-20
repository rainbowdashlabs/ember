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

const {t} = useI18n()

const roles = [
  {key: 'member', badge: PrimaryBadge, icon: ['fas', 'user']},
  {key: 'guardian', badge: SecondaryBadge, icon: ['fas', 'users']},
  {key: 'team', badge: InfoBadge, icon: ['fas', 'people-group']},
  {key: 'manager', badge: SuccessBadge, icon: ['fas', 'user-gear']},
  {key: 'admin', badge: ErrorBadge, icon: ['fas', 'shield']},
]

const managementRoles = [
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
  <HelpArticle :title="t('helpCenter.basics.roles.title')" :subtitle="t('helpCenter.basics.roles.subtitle')">
    <HelpSection :title="t('helpCenter.basics.roles.whatAre')">
      <p>{{ t('helpCenter.basics.roles.whatAreText') }}</p>
      <p>{{ t('helpCenter.basics.roles.whatAreText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.roles.overview')">
      <div class="space-y-3">
        <NeutralContainer v-for="r in roles" :key="r.key" class="flex items-start gap-3 p-4">
          <font-awesome-icon :icon="r.icon" class="h-5 w-5 text-primary mt-0.5 shrink-0"/>
          <div class="flex-1">
            <div class="flex items-center gap-2 mb-1">
              <component :is="r.badge">{{ t(`helpCenter.basics.roles.role.${r.key}.name`) }}</component>
            </div>
            <p class="text-sm">{{ t(`helpCenter.basics.roles.role.${r.key}.desc`) }}</p>
            <p class="text-xs text-(--text-muted) mt-1">{{ t(`helpCenter.basics.roles.role.${r.key}.example`) }}</p>
          </div>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.roles.hierarchy')">
      <p>{{ t('helpCenter.basics.roles.hierarchyText') }}</p>
      <NeutralContainer class="p-4 mt-3">
        <div class="flex flex-col items-center gap-1 text-sm">
          <ErrorBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.roles.role.admin.name') }}</ErrorBadge>
          <font-awesome-icon :icon="['fas', 'arrow-down']" class="text-(--text-muted)"/>
          <SuccessBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.roles.role.manager.name') }}</SuccessBadge>
          <font-awesome-icon :icon="['fas', 'arrow-down']" class="text-(--text-muted)"/>
          <InfoBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.roles.role.team.name') }}</InfoBadge>
          <font-awesome-icon :icon="['fas', 'arrow-down']" class="text-(--text-muted)"/>
          <div class="flex gap-4">
            <PrimaryBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.roles.role.member.name') }}</PrimaryBadge>
            <SecondaryBadge class="text-base px-4 py-1">{{ t('helpCenter.basics.roles.role.guardian.name') }}</SecondaryBadge>
          </div>
        </div>
      </NeutralContainer>
      <p class="mt-3">{{ t('helpCenter.basics.roles.hierarchyText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.roles.management')">
      <p>{{ t('helpCenter.basics.roles.managementText') }}</p>
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-3">
        <NeutralContainer v-for="mr in managementRoles" :key="mr" class="p-3">
          <p class="font-semibold text-sm">{{ t(`helpCenter.basics.roles.mgmt.${mr}.name`) }}</p>
          <p class="text-xs text-(--text-muted)">{{ t(`helpCenter.basics.roles.mgmt.${mr}.desc`) }}</p>
        </NeutralContainer>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.roles.groups')">
      <p>{{ t('helpCenter.basics.roles.groupsText') }}</p>
      <p>{{ t('helpCenter.basics.roles.groupsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.basics.roles.owner')">
      <p>{{ t('helpCenter.basics.roles.ownerText') }}</p>
      <ul class="list-disc pl-5 space-y-1">
        <li>{{ t('helpCenter.basics.roles.owner1') }}</li>
        <li>{{ t('helpCenter.basics.roles.owner2') }}</li>
        <li>{{ t('helpCenter.basics.roles.owner3') }}</li>
      </ul>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.basics.roles.tip') }}</HelpTip>
  </HelpArticle>
</template>
