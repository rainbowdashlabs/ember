/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ConsentGate from '@/components/consent/ConsentGate.vue'
import LegalModal from '@/components/consent/LegalModal.vue'
import type {LoginConsent} from '@/composables/useLoginConsent'

/**
 * The consent step every way in passes before its own form: the sign-in form, setting a password
 * from a link and setting up a passkey. Whoever arrives by a link has never seen the sign-in form,
 * so asking only there left them signed in without ever having been asked.
 *
 * <p>The page shows its own form once {@code legal.consent} is accepted; until then this stands in
 * its place. The consent is the page's own state, handed in so the page can tell when it is given.
 *
 * <p>A refusal is remembered in the browser, so it is never final here: next to it sits the way back
 * to the choice. Without it, one press on "Ablehnen" left the page showing nothing but the refusal on
 * every visit, until the site data was cleared by hand.
 */
const props = defineProps<{
  legal: LoginConsent
}>()

const {
  consent, scopes, consentHtml, consentLoading,
  showPrivacyPolicy, privacyPolicyHtml, privacyPolicyLoading,
  showTos, tosHtml, tosLoading,
  acceptCurrentVersions, deny, reconsider, loadPrivacyPolicy, loadTos,
} = props.legal

const {t} = useI18n()
</script>

<template>
  <ConsentGate v-if="consent === null"
               v-model:scopes="scopes"
               :consent-loading="consentLoading" :consent-html="consentHtml"
               @accept="acceptCurrentVersions" @deny="deny"
               @show-privacy="loadPrivacyPolicy" @show-tos="loadTos"/>

  <div v-if="consent === 'denied'" class="space-y-3">
    <Alert variant="error">
      {{ t('login.storageDenied') }}
    </Alert>
    <SecondaryButton class="w-full" @click="reconsider">{{ t('login.storageReconsider') }}</SecondaryButton>
  </div>

  <LegalModal v-model="showPrivacyPolicy" :title="t('storageConsent.privacyPolicyTitle')"
              :loading="privacyPolicyLoading" :html="privacyPolicyHtml"/>

  <LegalModal v-model="showTos" :title="t('storageConsent.tosTitle')"
              :loading="tosLoading" :html="tosHtml"/>
</template>
