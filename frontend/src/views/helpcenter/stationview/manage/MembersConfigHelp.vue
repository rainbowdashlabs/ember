/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import DummyFieldsPanel from './membersconfighelp/DummyFieldsPanel.vue'
import DummyTemplatesPanel from './membersconfighelp/DummyTemplatesPanel.vue'

const {t} = useI18n()

const activeTab = ref('MEMBER')
const tabs = [
  {key: 'MEMBER', label: t('membersConfig.tabMember')},
  {key: 'GUARDIAN', label: t('membersConfig.tabGuardian')},
  {key: 'TEAM', label: t('membersConfig.tabTeam')},
  {key: 'MANAGER', label: t('membersConfig.tabStationManager')},
  {key: 'GROUP', label: t('membersConfig.tabGroup')},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersConfig.title')" :subtitle="t('helpCenter.membersConfig.subtitle')">
    <HelpSection :title="t('helpCenter.membersConfig.whatIs')">
      <p>{{ t('helpCenter.membersConfig.whatIsText') }}</p>
      <p>{{ t('helpCenter.membersConfig.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersConfig.byRoleTitle')">
      <p>{{ t('helpCenter.membersConfig.byRoleText') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleMember') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleGuardian') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleTeam') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleStationManager') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleGroup') }}</p>
    </HelpSection>

    <TabBar v-model="activeTab" :tabs="tabs"/>

    <DummyFieldsPanel/>

    <HelpSection :title="t('helpCenter.membersConfig.templatesTitle')">
      <p>{{ t('helpCenter.membersConfig.templatesText') }}</p>
    </HelpSection>

    <DummyTemplatesPanel/>

    <HelpSection :title="t('helpCenter.membersConfig.optionsTitle')">
      <p>{{ t('helpCenter.membersConfig.optRequired') }}</p>
      <p>{{ t('helpCenter.membersConfig.optReadonly') }}</p>
      <p>{{ t('helpCenter.membersConfig.optNotify') }}</p>
      <p>{{ t('helpCenter.membersConfig.optOverview') }}</p>
      <p>{{ t('helpCenter.membersConfig.optKeep') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersConfig.typesTitle')">
      <p>{{ t('helpCenter.membersConfig.typeText') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeNumber') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeDate') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeBirthDate') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeBoolean') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeEnum') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeAge') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersConfig.tip') }}</HelpTip>
  </HelpArticle>
</template>
