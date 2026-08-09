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
import InfoContainer from '@/components/container/InfoContainer.vue'
import GroupList from '@/views/stationview/setup/steps/groupsstep/GroupList.vue'
import GroupEditor from '@/views/stationview/setup/steps/groupsstep/GroupEditor.vue'
import WizardFrame from './setuphelp/WizardFrame.vue'

const {t} = useI18n()

const GROUPS = [
  {id: 1, stationId: 'demo', name: 'Jugendgruppe', color: '#FF6421', position: 0},
  {id: 2, stationId: 'demo', name: 'Atemschutz', color: '#3694FF', position: 1},
  {id: 3, stationId: 'demo', name: 'Gerätewart', color: null, position: 2},
]

const dummyRoles = [
  {id: 1, permission: 'EVENT_EDIT'},
  {id: 2, permission: 'MEMBER_EDIT'},
]
const dummyPermissions = new Set([1])
</script>

<template>
  <HelpArticle :title="t('helpCenter.setupGroups.title')" :subtitle="t('helpCenter.setupGroups.subtitle')">
    <HelpSection :title="t('helpCenter.setupGroups.whatIs')">
      <p>{{ t('helpCenter.setupGroups.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupGroups.howTo')">
      <p>{{ t('helpCenter.setupGroups.howToStep1') }}</p>
      <p>{{ t('helpCenter.setupGroups.howToStep2') }}</p>
      <p>{{ t('helpCenter.setupGroups.howToStep3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupGroups.exampleTitle')">
      <p>{{ t('helpCenter.setupGroups.exampleText') }}</p>
      <WizardFrame step-id="groups" skippable>
        <InfoContainer class="space-y-2">
          <p class="font-medium text-sm">{{ t('setup.steps.groups.aboutTitle') }}</p>
          <p class="text-sm">{{ t('setup.steps.groups.aboutBody') }}</p>
          <ul class="list-disc list-inside text-sm space-y-1">
            <li>{{ t('setup.steps.groups.useCase1') }}</li>
            <li>{{ t('setup.steps.groups.useCase2') }}</li>
            <li>{{ t('setup.steps.groups.useCase3') }}</li>
          </ul>
        </InfoContainer>
        <div class="grid gap-6 lg:grid-cols-2">
          <GroupList :draft="''" :groups="GROUPS" :selected-id="2"/>
          <GroupEditor :group="GROUPS[1]!" color="#3694FF" :all-roles="dummyRoles"
                       :permissions="dummyPermissions" :permissions-loading="false"/>
        </div>
      </WizardFrame>
    </HelpSection>

    <HelpSection :title="t('helpCenter.setupGroups.orderTitle')">
      <p>{{ t('helpCenter.setupGroups.orderText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.setupGroups.tip') }}</HelpTip>
  </HelpArticle>
</template>
