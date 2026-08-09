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
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()

const USER_TYPES = [
  {label: t('helpCenter.typePermissions.typeTrial'), desc: t('helpCenter.typePermissions.typeTrialDesc')},
  {label: t('helpCenter.typePermissions.typeMember'), desc: t('helpCenter.typePermissions.typeMemberDesc')},
  {label: t('helpCenter.typePermissions.typeGuardian'), desc: t('helpCenter.typePermissions.typeGuardianDesc')},
  {label: t('helpCenter.typePermissions.typeTeam'), desc: t('helpCenter.typePermissions.typeTeamDesc')},
  {label: t('helpCenter.typePermissions.typeManager'), desc: t('helpCenter.typePermissions.typeManagerDesc')},
] as const

const dummyRoles = [
  {id: 1, permission: 'LOGIN'},
  {id: 2, permission: 'EVENT_EDIT'},
  {id: 3, permission: 'MEMBER_EDIT'},
]
const dummySelected = new Set([1, 2])
const dummyLocked = new Map([['LOGIN', t('permissions.lockedByUserType')]])
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupMemberTypes.title')" :subtitle="t('helpCenter.setupMemberTypes.subtitle')">
    <HelpSection :title="t('helpCenter.setupMemberTypes.whatIs')">
      <p>{{ t('helpCenter.setupMemberTypes.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupMemberTypes.howTo')">
      <p>{{ t('helpCenter.setupMemberTypes.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupMemberTypes.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupMemberTypes.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupMemberTypes.exampleTitle')">
      <p>{{ t('helpCenter.setupMemberTypes.exampleText') }}</p>
      <WizardFrame step-id="member-types">
        <div class="grid gap-6 lg:grid-cols-2">
          <div class="space-y-4">
            <SectionHeader>{{ t('userTypePermissions.title') }}</SectionHeader>
            <MutedText size="sm">{{ t('userTypePermissions.description') }}</MutedText>
            <div class="space-y-2">
              <NeutralContainer v-for="(ut, index) in USER_TYPES" :key="ut.label"
                                :class="index === 3 ? 'border-primary' : 'hover:border-primary'"
                                class="cursor-pointer transition-colors">
                <div class="font-medium">{{ ut.label }}</div>
                <MutedText size="sm">{{ ut.desc }}</MutedText>
              </NeutralContainer>
            </div>
          </div>
          <div class="space-y-4">
            <SectionHeader>{{ t('helpCenter.typePermissions.typeTeam') }}</SectionHeader>
            <PermissionPicker :model-value="dummySelected" :all-roles="dummyRoles"
                              :locked-permissions="dummyLocked"/>
          </div>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupMemberTypes.lockedTitle')">
      <p>{{ t('helpCenter.setupMemberTypes.lockedText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupMemberTypes.tip') }}</HelpTip>
  </HelpArticle>
</template>
