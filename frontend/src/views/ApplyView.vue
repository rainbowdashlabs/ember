/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {stationApplications} from '@/api'

const {t} = useI18n()

const firstName = ref('')
const lastName = ref('')
const email = ref('')
const stationName = ref('')
const introduction = ref('')
const submitting = ref(false)
const error = ref('')
const submitted = ref(false)

async function submit() {
  if (!firstName.value.trim() || !lastName.value.trim() || !email.value.trim() || !stationName.value.trim()) {
    error.value = t('apply.allFieldsRequired')
    return
  }
  submitting.value = true
  error.value = ''
  try {
    await stationApplications.submit({
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      email: email.value.trim(),
      stationName: stationName.value.trim(),
      introduction: introduction.value.trim(),
    })
    submitted.value = true
  } catch {
    error.value = t('common.error')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex items-center justify-center px-4 py-12">
    <div class="w-full max-w-md space-y-6">
      <div class="text-center">
        <font-awesome-icon :icon="['fas', 'building']" class="text-4xl text-primary mb-3"/>
        <h1 class="text-2xl font-bold">{{ t('apply.title') }}</h1>
      </div>

      <template v-if="!submitted">
        <p class="text-sm text-(--text-muted)">{{ t('apply.hint') }}</p>

        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <form class="space-y-4" @submit.prevent="submit">
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('apply.firstName') }}</label>
            <TextInput v-model="firstName" :placeholder="t('apply.firstNamePlaceholder')"/>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('apply.lastName') }}</label>
            <TextInput v-model="lastName" :placeholder="t('apply.lastNamePlaceholder')"/>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('apply.email') }}</label>
            <TextInput v-model="email" :placeholder="t('apply.emailPlaceholder')"/>
            <p class="text-xs text-(--text-muted)">{{ t('apply.emailHint') }}</p>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('apply.stationName') }}</label>
            <TextInput v-model="stationName" :placeholder="t('apply.stationNamePlaceholder')"/>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('apply.introduction') }}</label>
            <TextAreaInput v-model="introduction" :placeholder="t('apply.introductionPlaceholder')"/>
            <p class="text-xs text-(--text-muted)">{{ t('apply.introductionHint') }}</p>
          </div>
          <PrimaryButton :disabled="submitting" class="w-full" type="submit">
            {{ submitting ? t('common.loading') : t('apply.submit') }}
          </PrimaryButton>
        </form>

        <div class="text-center">
          <router-link class="text-sm text-primary hover:underline" to="/login">{{
              t('apply.backToLogin')
            }}
          </router-link>
        </div>
      </template>

      <template v-else>
        <Alert variant="success">{{ t('apply.success') }}</Alert>
        <div class="text-center">
          <router-link class="text-sm text-primary hover:underline" to="/login">{{
              t('apply.backToLogin')
            }}
          </router-link>
        </div>
      </template>
    </div>
  </div>
</template>
