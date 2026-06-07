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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'

const {t} = useI18n()

const USER_TYPES = [
  {label: t('helpCenter.typePermissions.typeTrial'), desc: t('helpCenter.typePermissions.typeTrialDesc')},
  {label: t('helpCenter.typePermissions.typeMember'), desc: t('helpCenter.typePermissions.typeMemberDesc')},
  {label: t('helpCenter.typePermissions.typeGuardian'), desc: t('helpCenter.typePermissions.typeGuardianDesc')},
  {label: t('helpCenter.typePermissions.typeTeam'), desc: t('helpCenter.typePermissions.typeTeamDesc')},
  {label: t('helpCenter.typePermissions.typeManager'), desc: t('helpCenter.typePermissions.typeManagerDesc')},
] as const

const dummyRoles = [
  {id: 1, permission: 'ATTENDANCE_MANAGEMENT', label: 'Anwesenheitsverwaltung'},
  {id: 2, permission: 'INVENTORY_READ', label: 'Inventar lesen'},
  {id: 3, permission: 'EVENT_MANAGEMENT', label: 'Terminverwaltung'},
]
const dummySelected = new Set([1])
</script>

<template>
  <HelpArticle :title="t('helpCenter.typePermissions.title')" :subtitle="t('helpCenter.typePermissions.subtitle')">
    <HelpSection :title="t('helpCenter.typePermissions.whatIs')">
      <p>{{ t('helpCenter.typePermissions.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.typePermissions.howTo')">
      <p>{{ t('helpCenter.typePermissions.howToText') }}</p>
    </HelpSection>

    <!-- Dummy: Two-column grid matching the real view -->
    <HelpSection :title="t('helpCenter.typePermissions.exampleTitle')">
      <div class="grid gap-6 lg:grid-cols-2">
        <!-- User type list -->
        <div class="space-y-4">
          <SectionHeader>{{ t('userTypePermissions.title') }}</SectionHeader>
          <MutedText size="sm">{{ t('userTypePermissions.description') }}</MutedText>

          <div class="space-y-2">
            <NeutralContainer
                v-for="(ut, index) in USER_TYPES"
                :key="index"
                :class="index === 3 ? 'border-primary' : 'hover:border-primary'"
                class="cursor-pointer transition-colors"
            >
              <div class="font-medium">{{ ut.label }}</div>
              <MutedText size="sm">{{ ut.desc }}</MutedText>
            </NeutralContainer>
          </div>
        </div>

        <!-- Permission picker -->
        <div class="space-y-4">
          <SectionHeader>{{ t('helpCenter.typePermissions.typeTeam') }}</SectionHeader>
          <PermissionPicker :model-value="dummySelected" :all-roles="dummyRoles"/>
        </div>
      </div>
    </HelpSection>

    <HelpSection :title="t('helpCenter.typePermissions.stackingTitle')">
      <p>{{ t('helpCenter.typePermissions.stackingText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.typePermissions.tip') }}</HelpTip>
  </HelpArticle>
</template>
