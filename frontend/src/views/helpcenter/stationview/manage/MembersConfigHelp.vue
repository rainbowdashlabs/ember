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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import Td from '@/components/table/Td.vue'
import Th from '@/components/table/Th.vue'

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

    <!-- Dummy: Tab bar -->
    <TabBar v-model="activeTab" :tabs="tabs"/>

    <!-- Dummy: Field table -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('membersConfig.fields') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']" disabled>
          {{ t('membersConfig.addField') }}
        </PrimaryButton>
      </div>

      <p class="text-sm text-(--text-muted)">{{ t('membersConfig.memberHint') }}</p>

      <table class="w-full text-sm">
        <thead>
          <THead>
            <th class="py-2 px-3 font-medium">{{ t('membersConfig.colName') }}</th>
            <th class="py-2 px-3 font-medium">{{ t('membersConfig.colType') }}</th>
            <Th align="center" class="font-medium">
              <font-awesome-icon :icon="['fas', 'asterisk']" class="h-3 w-3" :title="t('helpCenter.membersConfig.optRequired')"/>
            </Th>
            <Th align="center" class="font-medium">
              <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3" :title="t('helpCenter.membersConfig.optReadonly')"/>
            </Th>
            <th class="py-2 px-3 font-medium text-right"></th>
          </THead>
        </thead>
        <tbody>
          <TRow>
            <Td>Telefonnummer</Td>
            <Td class="text-(--text-muted)">Text</Td>
            <Td align="center"><ToggleInput :model-value="true" disabled/></Td>
            <Td align="center"><ToggleInput :model-value="false" disabled/></Td>
            <Td align="right">
              <div class="flex items-center justify-end gap-2">
                <EditButton disabled/>
                <DeleteButton disabled/>
              </div>
            </Td>
          </TRow>
          <TRow>
            <Td>Geburtsdatum</Td>
            <Td class="text-(--text-muted)">Datum</Td>
            <Td align="center"><ToggleInput :model-value="true" disabled/></Td>
            <Td align="center"><ToggleInput :model-value="true" disabled/></Td>
            <Td align="right">
              <div class="flex items-center justify-end gap-2">
                <EditButton disabled/>
                <DeleteButton disabled/>
              </div>
            </Td>
          </TRow>
          <TRow>
            <Td>Kleidergröße</Td>
            <Td class="text-(--text-muted)">Auswahl</Td>
            <Td align="center"><ToggleInput :model-value="false" disabled/></Td>
            <Td align="center"><ToggleInput :model-value="false" disabled/></Td>
            <Td align="right">
              <div class="flex items-center justify-end gap-2">
                <EditButton disabled/>
                <DeleteButton disabled/>
              </div>
            </Td>
          </TRow>
        </tbody>
      </table>
    </NeutralContainer>

    <!-- Dummy: Field templates -->
    <HelpSection :title="t('helpCenter.membersConfig.templatesTitle')">
      <p>{{ t('helpCenter.membersConfig.templatesText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-3">
      <FieldLabel hint>{{ t('membersConfig.templates') }}</FieldLabel>
      <div class="flex flex-wrap gap-2">
        <SecondaryButton :icon="['fas', 'house']" disabled>Adresse</SecondaryButton>
        <SecondaryButton :icon="['fas', 'calendar-plus']" disabled>Geburtsdatum</SecondaryButton>
        <SecondaryButton :icon="['fas', 'phone']" disabled>Festnetz</SecondaryButton>
        <SecondaryButton :icon="['fas', 'mobile-screen']" disabled>Mobilnummer</SecondaryButton>
        <SecondaryButton :icon="['fas', 'triangle-exclamation']" disabled>Notfallkontakt</SecondaryButton>
        <SecondaryButton :icon="['fas', 'id-card']" disabled>Führerschein</SecondaryButton>
        <SecondaryButton :icon="['fas', 'calendar-plus']" disabled>Beitrittsdatum</SecondaryButton>
        <SecondaryButton :icon="['fas', 'hashtag']" disabled>Personalnummer</SecondaryButton>
        <SecondaryButton :icon="['fas', 'rainbow']" disabled>Geschlecht</SecondaryButton>
        <SecondaryButton :icon="['fas', 'fire']" disabled>Jugendflamme</SecondaryButton>
        <SecondaryButton :icon="['fas', 'medal']" disabled>Leistungsspange</SecondaryButton>
      </div>
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
      <p>{{ t('helpCenter.membersConfig.typeBoolean') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeEnum') }}</p>
      <p>{{ t('helpCenter.membersConfig.typeAge') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.membersConfig.tip') }}</HelpTip>
  </HelpArticle>
</template>
