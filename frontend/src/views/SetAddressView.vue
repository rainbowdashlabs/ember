/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {auth} from '@/api'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import PageHeroIcon from '@/components/typography/PageHeroIcon.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'
import {apiErrorMessage} from '@/util/apiError'
import {decideSignInLanding} from '@/util/signInLanding'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()

const email = ref('')

const token = route.query.token as string

/**
 * Gives the account an address and carries on into the application.
 *
 * <p>The step stands where the forced password change stands and behaves the same way: the token it
 * was handed instead of a session is spent here, and what comes back is either the session or the
 * second factor still to be given. A token that has run out sends the reader back to sign in again,
 * which is where a fresh one is handed out.
 */
const {running: loading, error: submitError, run: submit} = useAsyncAction(async () => {
  const result = await auth.setAddress({token, email: email.value})

  if (result.twoFactorRequired && result.preAuthToken) {
    await router.push({path: '/2fa-verify', query: {token: result.preAuthToken}})
    return
  }
  if (!result.token) {
    await router.push({name: 'login'})
    return
  }

  clearActiveStation()
  clearActiveCluster()
  const landing = await decideSignInLanding()
  if (landing.stationId) setActiveStation(landing.stationId)
  if (landing.clusterUid) setActiveCluster(landing.clusterUid)
  await router.push(landing.path)
}, {formatError: (e) => {
  const raw = apiErrorMessage(e)
  return raw ? (te(raw) ? t(raw) : raw) : t('common.error')
}})

const error = computed(() => submitError.value)
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div class="w-full max-w-xs space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'envelope']"/>
        <PageHeader class="text-2xl font-bold">{{ t('setAddress.title') }}</PageHeader>
        <p class="text-sm text-(--text-muted) mt-2">{{ t('setAddress.hint') }}</p>
      </div>

      <form class="space-y-4" @submit.prevent="submit">
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <div class="space-y-1">
          <FieldLabel>{{ t('setAddress.email') }}</FieldLabel>
          <TextInput v-model="email" :disabled="loading" :placeholder="t('setAddress.placeholder')"
                     data-testid="set-address-email" type="email"/>
        </div>

        <PrimaryButton :disabled="loading || !email" class="w-full" @click="submit">
          {{ loading ? t('common.loading') : t('setAddress.submit') }}
        </PrimaryButton>
      </form>
    </div>
  </div>
</template>
