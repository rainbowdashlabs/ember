/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SaveButton from '@/components/button/SaveButton.vue'

defineProps<{
  emailChangePending: boolean
  action: () => Promise<void>
}>()

const firstName = defineModel<string>('firstName', {required: true})
const lastName = defineModel<string>('lastName', {required: true})
const email = defineModel<string>('email', {required: true})
const username = defineModel<string>('username', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('profile.accountTitle') }}</SectionHeader>
    <div class="grid gap-4 sm:grid-cols-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('profile.firstName') }}</FieldLabel>
        <TextInput v-model="firstName"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('profile.lastName') }}</FieldLabel>
        <TextInput v-model="lastName"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('profile.email') }}</FieldLabel>
        <TextInput v-model="email"/>
      </div>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('profile.username') }}</FieldLabel>
      <TextInput v-model="username" :placeholder="email"/>
      <MutedText tag="p" size="sm">{{ t('profile.usernameHint') }}</MutedText>
    </div>
    <Alert v-if="emailChangePending" variant="info">{{ t('profile.emailChangePending') }}</Alert>
    <SaveButton :action="action">{{ t('profile.saveAccount') }}</SaveButton>
  </NeutralContainer>
</template>
