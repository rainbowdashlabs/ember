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
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SearchSelectInput from '@/components/input/select/SearchSelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'

const {t} = useI18n()

const timezoneOptions = [
  {value: 'Europe/Berlin', label: 'Europe/Berlin'},
  {value: 'Europe/London', label: 'Europe/London'},
  {value: 'Europe/Paris', label: 'Europe/Paris'},
]
</script>

<template>
  <HelpArticle :title="t('helpCenter.stationManage.title')" :subtitle="t('helpCenter.stationManage.subtitle')">
    <HelpSection :title="t('helpCenter.stationManage.whoCanSee')">
      <p>{{ t('helpCenter.stationManage.whoCanSeeText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationManage.nameTitle')">
      <p>{{ t('helpCenter.stationManage.nameText') }}</p>
    </HelpSection>

    <!-- Dummy: Station name & settings -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.nameTitle') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('stationManage.name') }}</FieldLabel>
        <TextInput model-value="Jugendfeuerwehr Musterstadt" :placeholder="t('stationManage.namePlaceholder')" disabled/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('stationManage.timezone') }}</FieldLabel>
        <SearchSelectInput model-value="Europe/Berlin" :options="timezoneOptions" :placeholder="t('stationManage.timezone')" disabled/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('stationManage.locale') }}</FieldLabel>
        <SelectInput model-value="de-DE" disabled>
          <option value="de-DE">Deutsch</option>
        </SelectInput>
      </div>
      <PrimaryButton disabled>{{ t('stationManage.save') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.stationManage.logoTitle')">
      <p>{{ t('helpCenter.stationManage.logoText') }}</p>
    </HelpSection>

    <!-- Dummy: Logo upload area -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.logoTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.noLogo') }}</p>
      <FileUploadButton disabled accept="image/png,image/jpeg,image/webp,image/svg+xml">
        {{ t('stationManage.uploadLogo') }}
      </FileUploadButton>
      <p class="text-xs text-(--text-muted)">{{ t('stationManage.logoHint') }}</p>
    </NeutralContainer>

    <!-- Owner section -->
    <HelpSection :title="t('helpCenter.stationManage.ownerTitle')">
      <p>{{ t('helpCenter.stationManage.ownerText') }}</p>
    </HelpSection>

    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.ownerHandoverTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.ownerHandoverHint') }}</p>
      <SelectInput model-value="" disabled class="w-full">
        <option value="">{{ t('stationManage.ownerHandoverSelect') }}</option>
        <option value="1">Anna Schmidt</option>
        <option value="2">Thomas Weber</option>
      </SelectInput>
      <PrimaryButton disabled>{{ t('stationManage.ownerHandoverSubmit') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.stationManage.deleteStationTitle')">
      <p>{{ t('helpCenter.stationManage.deleteStationText') }}</p>
    </HelpSection>

    <ErrorContainer class="space-y-4">
      <SectionHeader>{{ t('stationManage.deleteTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('stationManage.deleteHint') }}</p>
      <ErrorButton :icon="['fas', 'trash']" disabled>
        {{ t('stationManage.deleteRequest') }}
      </ErrorButton>
    </ErrorContainer>

    <HelpSection :title="t('helpCenter.stationManage.mailTitle')">
      <p>{{ t('helpCenter.stationManage.mailText') }}</p>
      <p>
        <router-link :to="{name: 'help-station-mail-config'}" class="text-primary hover:underline">
          {{ t('helpCenter.stationManage.mailLink') }}
        </router-link>
      </p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.stationManage.tip') }}</HelpTip>
  </HelpArticle>
</template>
