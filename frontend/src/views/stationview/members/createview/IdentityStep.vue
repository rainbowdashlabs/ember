/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SetupMailChoice from '@/components/input/toggle/SetupMailChoice.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

const firstName = defineModel<string>('firstName', {required: true})
const lastName = defineModel<string>('lastName', {required: true})
const email = defineModel<string>('email', {required: true})
const canLogin = defineModel<boolean>('canLogin', {required: true})
const sendSetupMail = defineModel<boolean>('sendSetupMail', {required: true})

const emit = defineEmits<{
  next: []
  back: []
}>()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('membersCreate.stepIdentity') }}</SectionHeader>

    <div class="grid gap-4 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('membersCreate.firstName') }} <span
            class="text-error">*</span></FieldLabel>
        <TextInput v-model="firstName" :placeholder="t('membersCreate.firstNamePlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('membersCreate.lastName') }} <span
            class="text-error">*</span></FieldLabel>
        <TextInput v-model="lastName" :placeholder="t('membersCreate.lastNamePlaceholder')"/>
      </div>
    </div>

    <div class="flex items-center justify-between">
      <div>
        <label class="text-sm font-medium">{{ t('membersCreate.canLogin') }}</label>
        <p class="text-xs text-(--text-muted)">{{ t('membersCreate.canLoginHint') }}</p>
      </div>
      <ToggleInput v-model="canLogin"/>
    </div>

    <div v-if="canLogin" class="space-y-1">
      <FieldLabel>{{ t('membersCreate.email') }} <span class="text-error">*</span></FieldLabel>
      <TextInput v-model="email" :placeholder="t('membersCreate.emailPlaceholder')"/>
      <p class="text-xs text-(--text-muted)">{{ t('membersCreate.emailHint') }}</p>
    </div>

    <SetupMailChoice v-model="sendSetupMail" :has-address="canLogin"/>

    <div class="flex justify-between">
      <SecondaryButton @click="emit('back')">{{ t('membersCreate.back') }}</SecondaryButton>
      <PrimaryButton :disabled="!firstName || !lastName || (canLogin && !email)" @click="emit('next')">
        {{ t('membersCreate.next') }}
      </PrimaryButton>
    </div>
  </NeutralContainer>
</template>
