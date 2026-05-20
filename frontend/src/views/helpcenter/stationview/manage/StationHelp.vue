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
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FileUploadButton from '@/components/button/FileUploadButton.vue'

const {t} = useI18n()
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
        <label class="block text-sm font-medium">{{ t('stationManage.name') }}</label>
        <TextInput model-value="Jugendfeuerwehr Musterstadt" :placeholder="t('stationManage.namePlaceholder')" disabled/>
      </div>
      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('stationManage.timezone') }}</label>
        <SelectInput model-value="Europe/Berlin" disabled>
          <option value="Europe/Berlin">Europe/Berlin</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <label class="block text-sm font-medium">{{ t('stationManage.locale') }}</label>
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
