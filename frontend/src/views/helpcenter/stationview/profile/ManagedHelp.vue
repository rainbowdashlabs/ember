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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.managed.title')" :subtitle="t('helpCenter.managed.subtitle')">
    <HelpSection :title="t('helpCenter.managed.whatIs')">
      <p>{{ t('helpCenter.managed.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.managed.howTo')">
      <p>{{ t('helpCenter.managed.step1') }}</p>
      <p>{{ t('helpCenter.managed.step2') }}</p>
      <p>{{ t('helpCenter.managed.step3') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.managed.accessTitle')">
      <p>{{ t('helpCenter.managed.accessText') }}</p>
      <p>{{ t('helpCenter.managed.accessLoginText') }}</p>
    </HelpSection>

    <!-- Dummy: Member selector -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('profileManaged.title') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('profileManaged.selectMember') }}</FieldLabel>
        <SelectInput model-value="1">
          <option value="" disabled>{{ t('profileManaged.selectMemberPlaceholder') }}</option>
          <option value="1">Lena Mustermann</option>
          <option value="2">Tim Mustermann</option>
        </SelectInput>
      </div>
    </NeutralContainer>

    <!-- Dummy: Profile fields for selected member -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('profileManaged.fields') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>
          Telefonnummer <span class="text-error">*</span>
        </FieldLabel>
        <TextInput model-value="0170 1234567"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>
          Geburtstag <span class="text-error">*</span>
        </FieldLabel>
        <TextInput model-value="22.07.2015"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>
          Kleidergröße
        </FieldLabel>
        <TextInput model-value="140"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>
          Alter <MutedText class="ml-1">({{ t('profile.readonlyHint') }})</MutedText>
        </FieldLabel>
        <TextInput model-value="10" disabled/>
      </div>
      <PrimaryButton>{{ t('profile.save') }}</PrimaryButton>
    </NeutralContainer>

    <HelpTip>{{ t('helpCenter.managed.tip') }}</HelpTip>
  </HelpArticle>
</template>
