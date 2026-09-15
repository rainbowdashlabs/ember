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
import DummyAudiencesPanel from './membersconfighelp/DummyAudiencesPanel.vue'
import DummyTemplatesPanel from './membersconfighelp/DummyTemplatesPanel.vue'
import {STATION_ROLES} from '@/composables/useFieldsConfig'

const {t} = useI18n()

const activeTab = ref('MEMBER')
const tabs = STATION_ROLES.map(role => ({key: role, label: t(`membersConfig.roles.${role}`)}))
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersConfig.title')" :subtitle="t('helpCenter.membersConfig.subtitle')">
    <HelpSection :title="t('helpCenter.membersConfig.whatIs')">
      <p>{{ t('helpCenter.membersConfig.whatIsText') }}</p>
      <p>{{ t('helpCenter.membersConfig.whatIsText2') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersConfig.byRoleTitle')">
      <p>{{ t('helpCenter.membersConfig.byRoleText') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleTrial') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleMember') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleGuardian') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleTeam') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleStationManager') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleGroup') }}</p>
    </HelpSection>

    <div class="grid gap-6 lg:grid-cols-2 items-start">
      <DummyFieldsPanel/>
      <DummyAudiencesPanel/>
    </div>

    <HelpSection :title="t('helpCenter.membersConfig.audiencesTitle')">
      <p>{{ t('helpCenter.membersConfig.audiencesText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersConfig.nobodyTitle')">
      <p>{{ t('helpCenter.membersConfig.nobodyText') }}</p>
    </HelpSection>

    <TabBar v-model="activeTab" :tabs="tabs"/>

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
      <p>{{ t('helpCenter.membersConfig.typeSection') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersConfig.layoutTitle')">
      <p>{{ t('helpCenter.membersConfig.layoutText') }}</p>
      <p>{{ t('helpCenter.membersConfig.layoutPreview') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersConfig.tip') }}</HelpTip>
  </HelpArticle>
</template>
