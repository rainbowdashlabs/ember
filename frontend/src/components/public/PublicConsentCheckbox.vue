/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import {session} from '@/api'

const {t} = useI18n()

const accepted = defineModel<boolean>('accepted', {default: false})
const consentVersion = defineModel<string>('consentVersion', {default: ''})
const privacyVersion = defineModel<string>('privacyVersion', {default: ''})
const tosVersion = defineModel<string>('tosVersion', {default: ''})

const loadError = ref(false)
const loading = ref(true)

onMounted(async () => {
  try {
    const versions = await session.getLegalVersions()
    consentVersion.value = versions.consentVersion
    privacyVersion.value = versions.privacyVersion
    tosVersion.value = versions.tosVersion
  } catch {
    loadError.value = true
  } finally {
    loading.value = false
  }
})

watch([consentVersion, privacyVersion, tosVersion], () => {
  if (!consentVersion.value || !privacyVersion.value || !tosVersion.value) {
    accepted.value = false
  }
})
</script>

<template>
  <label class="consent">
    <CheckboxInput v-model="accepted" :disabled="loading || loadError"/>
    <span class="consent-text">
      <i18n-t keypath="publicConsent.label" tag="span">
        <template #privacy>
          <router-link to="/privacy" target="_blank" rel="noopener noreferrer" class="consent-link">
            {{ t('publicConsent.privacy') }}
          </router-link>
        </template>
        <template #tos>
          <router-link to="/terms" target="_blank" rel="noopener noreferrer" class="consent-link">
            {{ t('publicConsent.tos') }}
          </router-link>
        </template>
      </i18n-t>
    </span>
  </label>
  <p v-if="loadError" class="consent-error">{{ t('publicConsent.loadError') }}</p>
</template>

<style scoped>
.consent {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  cursor: pointer;
  line-height: 1.4;
}
.consent-text {
  font-size: 0.875rem;
}
.consent-link {
  color: var(--color-primary);
  text-decoration: underline;
}
.consent-error {
  margin-top: 0.4rem;
  font-size: 0.8rem;
  color: var(--color-error);
}
</style>
