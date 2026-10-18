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
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import UserTypesEditor from '@/views/stationview/manage/attendanceconfigedit/UserTypesEditor.vue'

const {t} = useI18n()

/** Three fields of a sheet as their cards show them, each with its type and the width it is drawn at. */
const sampleFields = [
  {name: t('helpCenter.exampleFields.remark'), type: 'TEXT', width: 'Full'},
  {name: t('helpCenter.exampleFields.rank'), type: 'CHOICE', width: 'Half'},
  {name: t('helpCenter.exampleFields.arrival'), type: 'TIME', width: 'Half'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceConfigEdit.title')" :subtitle="t('helpCenter.attendanceConfigEdit.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceConfigEdit.whatShown')">
      <p>{{ t('helpCenter.attendanceConfigEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.nameTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.nameText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('attendanceConfig.editTitle') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.name') }}</FieldLabel>
        <TextInput model-value="Übungsabend" :placeholder="t('attendanceConfig.namePlaceholder')" disabled/>
      </div>
      <PrimaryButton disabled>{{ t('attendanceConfig.save') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.userTypesTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.userTypesText') }}</p>
    </HelpSection>

    <UserTypesEditor :model-value="['TEAM']"/>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.groupsTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.groupsText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('attendanceConfig.groups') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('attendanceConfig.groupsHint') }}</p>
      <div class="space-y-2">
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <span class="text-sm font-medium">{{ t('helpCenter.sample.groups.beginners') }}</span>
          <div class="flex items-center gap-2">
            <MutedIcon :icon="['fas', 'chevron-up']"/>
            <MutedIcon :icon="['fas', 'chevron-down']"/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <span class="text-sm font-medium">{{ t('helpCenter.sample.groups.advancedPlural') }}</span>
          <div class="flex items-center gap-2">
            <MutedIcon :icon="['fas', 'chevron-up']"/>
            <MutedIcon :icon="['fas', 'chevron-down']"/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <span class="text-sm font-medium">{{ t('helpCenter.sample.groups.supervisors') }}</span>
          <div class="flex items-center gap-2">
            <MutedIcon :icon="['fas', 'chevron-up']"/>
            <MutedIcon :icon="['fas', 'chevron-down']"/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
      </div>
      <PrimaryButton :icon="['fas', 'plus']" disabled>
        {{ t('attendanceConfig.addGroup') }}
      </PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.fieldsTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.fieldsText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('attendanceConfig.fields') }}</SectionHeader>
        <SecondaryButton :icon="['fas', 'plus']" disabled>
          {{ t('attendanceConfig.addField') }}
        </SecondaryButton>
      </div>
      <div class="space-y-2">
        <div v-for="sample in sampleFields" :key="sample.name" class="rounded border border-(--border) p-3 space-y-3">
          <div class="grid grid-cols-1 sm:grid-cols-[1fr_auto_auto] gap-2 items-end">
            <div class="space-y-1">
              <FieldLabel>{{ t('attendanceConfig.fieldName') }}</FieldLabel>
              <TextInput :model-value="sample.name" disabled/>
            </div>
            <div class="space-y-1 sm:w-56">
              <FieldLabel>{{ t('fieldTypes.type') }}</FieldLabel>
              <SelectInput :model-value="sample.type" disabled>
                <option :value="sample.type">{{ t(`fieldTypes.label.${sample.type}`) }}</option>
              </SelectInput>
            </div>
            <DeleteButton class="justify-self-end" disabled/>
          </div>
          <div class="w-40 space-y-1">
            <FieldLabel>{{ t('membersConfig.fieldWidth') }}</FieldLabel>
            <SelectInput :model-value="sample.width" disabled>
              <option :value="sample.width">{{ t(`membersConfig.width${sample.width}`) }}</option>
            </SelectInput>
          </div>
        </div>
      </div>
      <PrimaryButton disabled>{{ t('attendanceConfig.saveFields') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.autoAttendTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.autoAttendText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.widthTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.widthText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.attendanceConfigEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
