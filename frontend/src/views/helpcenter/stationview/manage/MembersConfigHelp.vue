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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import QuestionValueDisplay from '@/components/display/QuestionValueDisplay.vue'
import FieldsPanel from '@/views/stationview/manage/membersconfig/FieldsPanel.vue'
import AudiencesPanel from '@/views/stationview/manage/membersconfig/AudiencesPanel.vue'
import TemplateButtons from '@/views/stationview/manage/membersconfig/TemplateButtons.vue'
import ExpiryDateFields from '@/views/stationview/manage/membersconfig/fieldmodal/ExpiryDateFields.vue'
import {STATION_ROLES} from '@/composables/useFieldsConfig'
import {FieldType} from '@/api/generated/schema'
import {expirySettingsOf, type ExpirySettings} from '@/util/expiry'
import {DEMO_AUDIENCE_COUNT, DEMO_FIELDS, demoAudiences, demoExpiryDates} from './fixtures'

const {t} = useI18n()

const activeTab = ref('MEMBER')
const tabs = STATION_ROLES.map(role => ({key: role, label: t(`membersConfig.roles.${role}`)}))

const selectedField = DEMO_FIELDS[1] ?? null
const audiences = demoAudiences(t)
const unaskedRoles = STATION_ROLES.filter(role => !audiences.some(audience => audience.target.role === role))
const noneChecked = new Set<number>()
const expirySettings = ref<ExpirySettings>(expirySettingsOf({warnFromDays: 90, reminderDays: [90, 30]}))
const expiryDates = demoExpiryDates()
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
      <FieldsPanel :fields="DEMO_FIELDS" :selected-id="selectedField?.id ?? null" :audience-count="DEMO_AUDIENCE_COUNT"
                   :checked-ids="noneChecked" :roles="STATION_ROLES" :groups="[]"/>
      <AudiencesPanel :field="selectedField" :audiences="audiences" :unasked-roles="unaskedRoles" :unasked-groups="[]"/>
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

    <NeutralContainer class="space-y-3">
      <FieldLabel hint>{{ t('membersConfig.templates') }}</FieldLabel>
      <TemplateButtons/>
    </NeutralContainer>

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
      <p>{{ t('helpCenter.membersConfig.typeExpiryDate') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeBoolean') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeEnum') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeGender') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeAge') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeSection') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.membersConfig.expiryTitle')">
      <p>{{ t('helpCenter.membersConfig.expiryStates') }}</p>
      <p>{{ t('helpCenter.membersConfig.expiryReminders') }}</p>
      <p>{{ t('helpCenter.membersConfig.expiryRecipients') }}</p>
      <p>{{ t('helpCenter.membersConfig.expiryFilter') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <ExpiryDateFields v-model="expirySettings"/>
      <div class="space-y-1">
        <FieldLabel>{{ t('fieldTypes.label.EXPIRY_DATE') }}</FieldLabel>
        <p v-for="date in expiryDates" :key="date" class="text-sm">
          <QuestionValueDisplay :value="date" :field-type="FieldType.EXPIRY_DATE" :config="{warnFromDays: 90}"/>
        </p>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersConfig.layoutTitle')">
      <p>{{ t('helpCenter.membersConfig.layoutText') }}</p>
      <p>{{ t('helpCenter.membersConfig.layoutPreview') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersConfig.tip') }}</HelpTip>
  </HelpArticle>
</template>
