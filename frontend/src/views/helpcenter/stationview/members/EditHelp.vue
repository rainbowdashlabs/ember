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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import {ref} from 'vue'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

const dummyFirstName = ref('Max')
const dummyLastName = ref('Mustermann')
const dummyEmail = ref('max@example.com')
</script>

<template>
  <HelpArticle :title="t('helpCenter.membersEdit.title')" :subtitle="t('helpCenter.membersEdit.subtitle')">
    <HelpSection :title="t('helpCenter.membersEdit.whatShown')">
      <p>{{ t('helpCenter.membersEdit.whatShownText') }}</p>
    </HelpSection>

    <!-- Dummy: Back button + header -->
    <SecondaryButton :icon="['fas', 'chevron-left']">
      {{ t('memberEdit.back') }}
    </SecondaryButton>
    <SectionHeader>Max Mustermann</SectionHeader>

    <HelpSection :title="t('helpCenter.membersEdit.accountTitle')">
      <p>{{ t('helpCenter.membersEdit.accountText') }}</p>
    </HelpSection>

    <!-- Dummy: Base fields -->
    <NeutralContainer class="space-y-4">
      <SubHeader class="text-sm">{{ t('memberEdit.baseFields') }}</SubHeader>
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.firstName') }}</FieldLabel>
          <TextInput v-model="dummyFirstName"/>
        </div>
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.lastName') }}</FieldLabel>
          <TextInput v-model="dummyLastName"/>
        </div>
        <div class="space-y-1">
          <FieldLabel hint>{{ t('memberEdit.email') }}</FieldLabel>
          <TextInput v-model="dummyEmail"/>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersEdit.rolesTitle')">
      <p>{{ t('helpCenter.membersEdit.rolesText') }}</p>
    </HelpSection>

    <!-- Dummy: PermissionGrant cards -->
    <NeutralContainer class="space-y-3">
      <SubHeader class="text-sm">{{ t('memberEdit.roles') }}</SubHeader>
      <div class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
        <div class="flex items-center justify-between rounded-lg px-3 py-2.5 border border-primary bg-primary/10 ring-2 ring-primary/30">
          <span class="text-sm font-medium">{{ t('membersCreate.roleMember') }}</span>
          <font-awesome-icon :icon="['fas', 'check']" class="text-primary"/>
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2.5 border border-primary bg-primary/10 ring-2 ring-primary/30">
          <span class="text-sm font-medium">{{ t('memberEdit.roleLogin') }}</span>
          <font-awesome-icon :icon="['fas', 'check']" class="text-primary"/>
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2.5 border border-bg-light-accent dark:border-bg-dark-accent hover:border-primary">
          <span class="text-sm font-medium">{{ t('membersCreate.roleTeam') }}</span>
        </div>
        <div class="flex items-center justify-between rounded-lg px-3 py-2.5 border border-bg-light-accent dark:border-bg-dark-accent opacity-40">
          <div>
            <span class="text-sm font-medium">{{ t('membersCreate.roleMemberManager') }}</span>
            <p class="text-xs text-(--text-muted)">{{ t('memberEdit.roleConflicting') }}</p>
          </div>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.membersEdit.fieldsTitle')">
      <p>{{ t('helpCenter.membersEdit.fieldsText') }}</p>
    </HelpSection>

    <!-- Dummy: Profile fields table -->
    <NeutralContainer class="space-y-4">
      <SubHeader class="text-sm">{{ t('memberEdit.fields') }}</SubHeader>
      <div class="overflow-x-auto">
        <table class="w-full text-sm">
          <thead>
            <THead>
              <Th class="text-(--text-muted)">{{ t('memberEdit.fieldName') }}</Th>
              <Th class="text-(--text-muted)">{{ t('memberEdit.fieldValue') }}</Th>
            </THead>
          </thead>
          <tbody>
            <TRow>
              <Td class="font-medium whitespace-nowrap">Telefon</Td>
              <Td>0170 1234567</Td>
            </TRow>
            <TRow>
              <Td class="font-medium whitespace-nowrap">Geburtstag</Td>
              <Td>2010-03-15</Td>
            </TRow>
            <TRow>
              <Td class="font-medium whitespace-nowrap">Kleidergröße</Td>
              <Td>M</Td>
            </TRow>
          </tbody>
        </table>
      </div>
    </NeutralContainer>

    <!-- Dummy: Save button -->
    <div class="flex items-center justify-between">
      <PrimaryButton>{{ t('memberEdit.save') }}</PrimaryButton>
    </div>

    <HelpTip>{{ t('helpCenter.membersEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
