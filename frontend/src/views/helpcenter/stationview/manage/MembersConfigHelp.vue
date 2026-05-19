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
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const {t} = useI18n()

const activeTab = ref('MEMBER')
const tabs = [
  {key: 'MEMBER', label: t('membersConfig.tabMember')},
  {key: 'GUARDIAN', label: t('membersConfig.tabManager')},
  {key: 'TEAM', label: t('membersConfig.tabTeam')},
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
      <p>{{ t('helpCenter.membersConfig.roleManager') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleTeam') }}</p>
      <p>{{ t('helpCenter.membersConfig.roleGroup') }}</p>
    </HelpSection>

    <!-- Dummy: Tab bar -->
    <TabBar v-model="activeTab" :tabs="tabs"/>

    <!-- Dummy: Field table -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('membersConfig.fields') }}</SectionHeader>
        <PrimaryButton disabled>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
          {{ t('membersConfig.addField') }}
        </PrimaryButton>
      </div>

      <p class="text-sm text-(--text-muted)">{{ t('membersConfig.memberHint') }}</p>

      <table class="w-full text-sm">
        <thead>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
            <th class="py-2 px-3 font-medium">{{ t('membersConfig.colName') }}</th>
            <th class="py-2 px-3 font-medium">{{ t('membersConfig.colType') }}</th>
            <th class="py-2 px-3 font-medium text-center">
              <font-awesome-icon :icon="['fas', 'asterisk']" class="h-3 w-3" :title="t('helpCenter.membersConfig.optRequired')"/>
            </th>
            <th class="py-2 px-3 font-medium text-center">
              <font-awesome-icon :icon="['fas', 'lock']" class="h-3 w-3" :title="t('helpCenter.membersConfig.optReadonly')"/>
            </th>
            <th class="py-2 px-3 font-medium text-right"></th>
          </tr>
        </thead>
        <tbody>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <td class="py-2 px-3">Telefonnummer</td>
            <td class="py-2 px-3 text-(--text-muted)">Text</td>
            <td class="py-2 px-3 text-center"><ToggleInput :model-value="true" disabled/></td>
            <td class="py-2 px-3 text-center"><ToggleInput :model-value="false" disabled/></td>
            <td class="py-2 px-3 text-right">
              <div class="flex items-center justify-end gap-2">
                <EditButton disabled/>
                <DeleteButton disabled/>
              </div>
            </td>
          </tr>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <td class="py-2 px-3">Geburtsdatum</td>
            <td class="py-2 px-3 text-(--text-muted)">Datum</td>
            <td class="py-2 px-3 text-center"><ToggleInput :model-value="true" disabled/></td>
            <td class="py-2 px-3 text-center"><ToggleInput :model-value="true" disabled/></td>
            <td class="py-2 px-3 text-right">
              <div class="flex items-center justify-end gap-2">
                <EditButton disabled/>
                <DeleteButton disabled/>
              </div>
            </td>
          </tr>
          <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent">
            <td class="py-2 px-3">Kleidergröße</td>
            <td class="py-2 px-3 text-(--text-muted)">Auswahl</td>
            <td class="py-2 px-3 text-center"><ToggleInput :model-value="false" disabled/></td>
            <td class="py-2 px-3 text-center"><ToggleInput :model-value="false" disabled/></td>
            <td class="py-2 px-3 text-right">
              <div class="flex items-center justify-end gap-2">
                <EditButton disabled/>
                <DeleteButton disabled/>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
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
