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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.attendanceConfigEdit.title')" :subtitle="t('helpCenter.attendanceConfigEdit.subtitle')">
    <HelpSection :title="t('helpCenter.attendanceConfigEdit.whatShown')">
      <p>{{ t('helpCenter.attendanceConfigEdit.whatShownText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.nameTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.nameText') }}</p>
    </HelpSection>

    <!-- Dummy: Template name field -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('attendanceConfig.editTitle') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('attendanceConfig.name') }}</FieldLabel>
        <TextInput model-value="Übungsabend" :placeholder="t('attendanceConfig.namePlaceholder')" disabled/>
      </div>
      <PrimaryButton disabled>{{ t('attendanceConfig.save') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.groupsTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.groupsText') }}</p>
    </HelpSection>

    <!-- Dummy: Groups list -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('attendanceConfig.groups') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('attendanceConfig.groupsHint') }}</p>
      <div class="space-y-2">
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <span class="text-sm font-medium">Anfänger</span>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'chevron-up']" class="text-(--text-muted) h-3 w-3"/>
            <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-(--text-muted) h-3 w-3"/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <span class="text-sm font-medium">Fortgeschrittene</span>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'chevron-up']" class="text-(--text-muted) h-3 w-3"/>
            <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-(--text-muted) h-3 w-3"/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <span class="text-sm font-medium">Betreuer</span>
          <div class="flex items-center gap-2">
            <font-awesome-icon :icon="['fas', 'chevron-up']" class="text-(--text-muted) h-3 w-3"/>
            <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-(--text-muted) h-3 w-3"/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
      </div>
      <PrimaryButton disabled>
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
        {{ t('attendanceConfig.addGroup') }}
      </PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.fieldsTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.fieldsText') }}</p>
    </HelpSection>

    <!-- Dummy: Fields list -->
    <NeutralContainer class="space-y-4">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('attendanceConfig.fields') }}</SectionHeader>
        <PrimaryButton disabled>
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
          {{ t('attendanceConfig.addField') }}
        </PrimaryButton>
      </div>
      <div class="space-y-2">
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <div>
            <span class="text-sm font-medium">{{ t('helpCenter.exampleFields.remark') }}</span>
            <span class="text-xs text-(--text-muted) ml-2">{{ t('helpCenter.fieldTypes.text') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton disabled/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <div>
            <span class="text-sm font-medium">{{ t('helpCenter.exampleFields.rank') }}</span>
            <span class="text-xs text-(--text-muted) ml-2">{{ t('helpCenter.fieldTypes.enum') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton disabled/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
        <NeutralContainer class="flex items-center justify-between py-2 px-3">
          <div>
            <span class="text-sm font-medium">{{ t('helpCenter.exampleFields.arrival') }}</span>
            <span class="text-xs text-(--text-muted) ml-2">{{ t('helpCenter.fieldTypes.time') }}</span>
          </div>
          <div class="flex items-center gap-2">
            <EditButton disabled/>
            <DeleteButton disabled/>
          </div>
        </NeutralContainer>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.attendanceConfigEdit.autoAttendTitle')">
      <p>{{ t('helpCenter.attendanceConfigEdit.autoAttendText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.attendanceConfigEdit.tip') }}</HelpTip>
  </HelpArticle>
</template>
