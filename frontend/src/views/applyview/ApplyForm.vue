/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

defineProps<{
  submitting: boolean
}>()

const emit = defineEmits<{
  submit: []
}>()

const firstName = defineModel<string>('firstName', {required: true})
const lastName = defineModel<string>('lastName', {required: true})
const email = defineModel<string>('email', {required: true})
const stationName = defineModel<string>('stationName', {required: true})
const introduction = defineModel<string>('introduction', {required: true})

const {t} = useI18n()
</script>

<template>
  <form class="space-y-4" @submit.prevent="emit('submit')">
    <div class="space-y-1">
      <FieldLabel>{{ t('apply.firstName') }}</FieldLabel>
      <TextInput v-model="firstName" :placeholder="t('apply.firstNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('apply.lastName') }}</FieldLabel>
      <TextInput v-model="lastName" :placeholder="t('apply.lastNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('apply.email') }}</FieldLabel>
      <TextInput v-model="email" :placeholder="t('apply.emailPlaceholder')"/>
      <p class="text-xs text-(--text-muted)">{{ t('apply.emailHint') }}</p>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('apply.stationName') }}</FieldLabel>
      <TextInput v-model="stationName" :placeholder="t('apply.stationNamePlaceholder')"/>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('apply.introduction') }}</FieldLabel>
      <TextAreaInput v-model="introduction" :placeholder="t('apply.introductionPlaceholder')"/>
      <p class="text-xs text-(--text-muted)">{{ t('apply.introductionHint') }}</p>
    </div>
    <PrimaryButton :disabled="submitting" class="w-full" type="submit">
      {{ submitting ? t('common.loading') : t('apply.submit') }}
    </PrimaryButton>
  </form>
</template>
