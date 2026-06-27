/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {stationMemberInvites} from '@/api'
import type {PublicStationMemberInviteResponse} from '@/api/stationMemberInvites'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const token = route.params.token as string
const invite = ref<PublicStationMemberInviteResponse | null>(null)
const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const password = ref('')
const passwordConfirm = ref('')

onMounted(async () => {
  try {
    invite.value = await stationMemberInvites.publicLookup(token)
  } catch {
    error.value = t('publicStationInvite.notFound')
  } finally {
    loading.value = false
  }
})

async function accept() {
  error.value = ''
  if (!password.value) {
    error.value = t('setPassword.required')
    return
  }
  if (password.value !== passwordConfirm.value) {
    error.value = t('setPassword.mismatch')
    return
  }
  submitting.value = true
  try {
    await stationMemberInvites.publicAccept(token, password.value)
    await router.push({name: 'login'})
  } catch (e: unknown) {
    const err = e as {response?: {data?: {message?: string}; status?: number}}
    if (err.response?.status === 410) {
      error.value = t('publicStationInvite.expiredOrUsed')
    } else if (err.response?.status === 400) {
      error.value = err.response?.data?.message ?? t('publicStationInvite.weakPassword')
    } else {
      error.value = t('common.error')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-sm space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'user-plus']"/>
        <PageHeader class="text-2xl font-bold">{{ t('publicStationInvite.title') }}</PageHeader>
      </div>

      <Spinner v-if="loading" size="lg" class="mx-auto"/>
      <Alert v-else-if="error && !invite" variant="error">{{ error }}</Alert>
      <template v-else-if="invite">
        <Alert v-if="invite.accepted" variant="info">{{ t('publicStationInvite.alreadyAccepted') }}</Alert>
        <Alert v-else-if="invite.expired" variant="error">{{ t('publicStationInvite.expired') }}</Alert>

        <form v-if="!invite.accepted && !invite.expired" class="space-y-4" @submit.prevent="accept">
          <p class="text-sm text-center">
            {{ t('publicStationInvite.greeting', {name: `${invite.firstName} ${invite.lastName}`.trim(), station: invite.stationName}) }}
          </p>
          <Alert v-if="error" variant="error">{{ error }}</Alert>

          <div class="space-y-1">
            <FieldLabel>{{ t('setPassword.newPassword') }}</FieldLabel>
            <PasswordInput v-model="password" :disabled="submitting"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('setPassword.confirmPassword') }}</FieldLabel>
            <PasswordInput v-model="passwordConfirm" :disabled="submitting"/>
          </div>
          <PrimaryButton :disabled="submitting || !password" class="w-full">
            {{ t('publicStationInvite.accept') }}
          </PrimaryButton>
        </form>
      </template>
    </div>
  </div>
</template>
