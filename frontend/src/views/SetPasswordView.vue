/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import PasswordInput from '@/components/input/text/PasswordInput.vue'
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
import LinkNoLongerGood from './setpasswordview/LinkNoLongerGood.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type {PasswordLinkStatus} from '@/api/auth'

const {t, te} = useI18n()
const route = useRoute()
const router = useRouter()
const {setActiveStation, clearActiveStation} = useStations()
const {setActiveCluster, clearActiveCluster} = useCluster()

const newPassword = ref('')
const confirmPassword = ref('')
const validationError = ref('')

const token = route.query.token as string

/**
 * What the link is worth, asked before the form is drawn.
 *
 * <p>Otherwise somebody types a password twice into a form that was never going to be accepted, and
 * is told only afterwards. While the answer is still coming nothing is shown but the spinner: a form
 * that appears and is then taken away reads worse than one that arrives a moment late.
 *
 * <p>The question is a courtesy. Where it cannot be answered at all the form is drawn anyway and the
 * submission decides, because a link that might be good must not be refused by a failed lookup.
 */
const status = ref<PasswordLinkStatus | null>(null)
const checking = ref(true)

onMounted(async () => {
  try {
    status.value = await auth.passwordLinkStatus(token)
  } catch {
    status.value = {standing: 'VALID', purpose: 'OTHER'}
  } finally {
    checking.value = false
  }
})

/**
 * Sets the password and goes wherever it leads.
 *
 * <p>Choosing a password proves what typing it into the sign-in form would prove, so the server
 * hands back a session and the reader carries on rather than signing in with the password they
 * chose ten seconds earlier. What it does not stand in for is a second factor: an account that has
 * one is sent to give it, exactly as signing in would, nor for an address the instance can write
 * to, which an administrator carrying none is sent to give first. An answer with none of the three
 * leaves the sign-in form to say what is still missing, which is where an unverified address is
 * explained.
 */
const {running: loading, error: submitError, run: runSetPassword} = useAsyncAction(async () => {
  const result = await auth.setPassword({token, password: newPassword.value})

  if (result.addressRequired && result.addressToken) {
    await router.push({path: '/set-address', query: {token: result.addressToken}})
    return
  }
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

const error = computed(() => validationError.value || submitError.value)

function handleSetPassword() {
  if (!newPassword.value) {
    validationError.value = t('setPassword.required')
    return
  }

  if (newPassword.value !== confirmPassword.value) {
    validationError.value = t('setPassword.mismatch')
    return
  }

  validationError.value = ''
  void runSetPassword()
}
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <Spinner v-if="checking" size="lg"/>

    <LinkNoLongerGood v-else-if="status && status.standing !== 'VALID'" :status="status"/>

    <div v-else class="w-full max-w-xs space-y-6">
      <div class="text-center">
        <PageHeroIcon :icon="['fas', 'lock']"/>
        <PageHeader class="text-2xl font-bold">{{ t('setPassword.title') }}</PageHeader>
      </div>

      <form class="space-y-4" @submit.prevent="handleSetPassword">
        <Alert v-if="error" variant="error">{{ error }}</Alert>

        <div class="space-y-1">
          <FieldLabel>{{ t('setPassword.newPassword') }}</FieldLabel>
          <PasswordInput
              v-model="newPassword"
              :disabled="loading"
              :placeholder="t('setPassword.newPassword')"
          />
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('setPassword.confirmPassword') }}</FieldLabel>
          <PasswordInput
              v-model="confirmPassword"
              :disabled="loading"
              :placeholder="t('setPassword.confirmPassword')"
          />
        </div>

        <PrimaryButton
            :disabled="loading || !newPassword || !confirmPassword"
            class="w-full"
            @click="handleSetPassword"
        >
          {{ loading ? t('common.loading') : t('setPassword.submit') }}
        </PrimaryButton>
      </form>
    </div>
  </div>
</template>
