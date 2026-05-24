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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PasswordInput from '@/components/input/text/PasswordInput.vue'

const {t} = useI18n()
</script>

<template>
  <HelpArticle :title="t('helpCenter.profile.title')" :subtitle="t('helpCenter.profile.subtitle')">
    <HelpSection :title="t('helpCenter.profile.whatIs')">
      <p>{{ t('helpCenter.profile.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.profile.accountTitle')">
      <p>{{ t('helpCenter.profile.accountText') }}</p>
    </HelpSection>

    <!-- Dummy: Account details form -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('profile.accountTitle') }}</SectionHeader>
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.firstName') }}</FieldLabel>
          <TextInput model-value="Max"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.lastName') }}</FieldLabel>
          <TextInput model-value="Mustermann"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.email') }}</FieldLabel>
          <TextInput model-value="max@beispiel.de"/>
        </div>
      </div>
      <PrimaryButton>{{ t('profile.saveAccount') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.profile.passwordTitle')">
      <p>{{ t('helpCenter.profile.passwordText') }}</p>
    </HelpSection>

    <!-- Dummy: Password change form -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('profile.passwordTitle') }}</SectionHeader>
      <div class="grid gap-4 sm:grid-cols-3">
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.currentPassword') }}</FieldLabel>
          <PasswordInput model-value="" :placeholder="t('profile.currentPassword')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.newPassword') }}</FieldLabel>
          <PasswordInput model-value="" :placeholder="t('profile.newPassword')"/>
        </div>
        <div class="space-y-1">
          <FieldLabel>{{ t('profile.confirmPassword') }}</FieldLabel>
          <PasswordInput model-value="" :placeholder="t('profile.confirmPassword')"/>
        </div>
      </div>
      <PrimaryButton disabled>{{ t('profile.changePassword') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.profile.fieldsTitle')">
      <p>{{ t('helpCenter.profile.fieldsText') }}</p>
      <p>{{ t('helpCenter.profile.requiredHint') }}</p>
      <p>{{ t('helpCenter.profile.readonlyHint') }}</p>
    </HelpSection>

    <!-- Dummy: Profile fields -->
    <NeutralContainer class="space-y-4">
      <SectionHeader>{{ t('profile.title') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>
          {{ t('helpCenter.exampleFields.phoneNumber') }} <span class="text-error">*</span>
        </FieldLabel>
        <TextInput model-value="0170 1234567"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>
          {{ t('helpCenter.exampleFields.birthday') }} <span class="text-error">*</span>
        </FieldLabel>
        <TextInput model-value="15.03.2010"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>
          {{ t('helpCenter.exampleFields.emergencyContact') }}
        </FieldLabel>
        <TextInput model-value="Erika Mustermann, 0170 9876543"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>
          {{ t('helpCenter.exampleFields.memberNumber') }} <span class="text-xs text-(--text-muted) ml-1">({{ t('profile.readonlyHint') }})</span>
        </FieldLabel>
        <TextInput model-value="M-2024-042" disabled/>
      </div>
      <PrimaryButton>{{ t('profile.save') }}</PrimaryButton>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.profile.incompleteTitle')">
      <p>{{ t('helpCenter.profile.incompleteText') }}</p>
    </HelpSection>

    <!-- Dummy: Incomplete fields warning -->
    <ErrorContainer class="space-y-2">
      <p class="font-semibold text-sm">{{ t('profile.incompleteTitle') }}</p>
      <p class="text-xs">{{ t('profile.incompleteHint') }}</p>
      <ul class="list-disc list-inside text-sm space-y-0.5">
        <li>{{ t('helpCenter.exampleFields.phoneNumber') }}</li>
        <li>{{ t('helpCenter.exampleFields.birthday') }}</li>
      </ul>
    </ErrorContainer>

    <HelpTip>{{ t('helpCenter.profile.tip') }}</HelpTip>
  </HelpArticle>
</template>
