/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {useI18n} from 'vue-i18n'
import {session} from '@/api'
import {acceptStorage} from '@/api/storage'
import {useConsentGuard} from '@/composables/useConsentGuard'
import type {ConsentChangesResponse} from '@/api/types'
import Spinner from '@/components/feedback/Spinner.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const {t} = useI18n()
const router = useRouter()

const loading = ref(true)
const submitting = ref(false)
const error = ref('')
const changes = ref<ConsentChangesResponse | null>(null)
const showPrivacyFull = ref(false)
const showTosFull = ref(false)

onMounted(async () => {
  try {
    const status = await session.getConsentStatus()
    if (status.current) {
      // Already up to date, shouldn't be here
      await router.replace({name: 'dashboard-overview'})
      return
    }
    changes.value = await session.getConsentChanges()
  } catch {
    error.value = t('common.error')
  }
  loading.value = false
})

function parseDiff(diff: string | undefined | null): { added: string[]; removed: string[] } {
  if (!diff) return {added: [], removed: []}
  const lines = diff.split('\n')
  const added: string[] = []
  const removed: string[] = []
  for (const line of lines) {
    if (line.startsWith('+ ') && line.trim() !== '+') {
      const content = line.substring(2).trim()
      if (content) added.push(content)
    } else if (line.startsWith('- ') && line.trim() !== '-') {
      const content = line.substring(2).trim()
      if (content) removed.push(content)
    }
  }
  return {added, removed}
}

async function handleAccept() {
  if (!changes.value) return
  submitting.value = true
  error.value = ''
  try {
    await session.recordConsent({
      consentVersion: changes.value.currentConsentVersion,
      privacyVersion: changes.value.currentPrivacyVersion,
      tosVersion: changes.value.currentTosVersion,
    })
    acceptStorage({
      consent: changes.value.currentConsentVersion,
      privacy: changes.value.currentPrivacyVersion,
      tos: changes.value.currentTosVersion,
    })
    const {setNeedsReconsent} = useConsentGuard()
    setNeedsReconsent(false)
    await router.replace({name: 'dashboard-overview'})
  } catch {
    error.value = t('common.error')
  }
  submitting.value = false
}
</script>

<template>
  <div class="flex justify-center px-4 py-12">
    <div class="w-full max-w-3xl space-y-6">
      <SectionHeader>{{ t('reconsent.title') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('reconsent.description') }}</p>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="changes && !loading">
        <!-- Privacy policy changes -->
        <NeutralContainer v-if="changes.privacyChanged" class="space-y-3">
          <h3 class="font-semibold">{{ t('reconsent.privacyChanged') }}</h3>

          <template v-if="changes.privacyDiff">
            <div class="text-xs text-(--text-muted) mb-1">{{ t('reconsent.whatChanged') }}</div>
            <div class="border border-(--border) rounded-lg p-3 text-sm space-y-1 max-h-48 overflow-y-auto bg-(--bg)">
              <div v-for="(line, i) in parseDiff(changes.privacyDiff).removed" :key="'pr-' + i"
                   class="text-error line-through">{{ line }}</div>
              <div v-for="(line, i) in parseDiff(changes.privacyDiff).added" :key="'pa-' + i"
                   class="text-success">{{ line }}</div>
            </div>
          </template>

          <button class="text-xs text-primary hover:underline cursor-pointer"
                  @click="showPrivacyFull = !showPrivacyFull">
            {{ showPrivacyFull ? t('reconsent.hideFullText') : t('reconsent.showFullText') }}
          </button>
          <div v-if="showPrivacyFull && changes.privacyHtml"
               class="legal-content max-h-96 overflow-y-auto border border-(--border) rounded-lg p-3"
               v-html="changes.privacyHtml"/>
        </NeutralContainer>

        <!-- ToS changes -->
        <NeutralContainer v-if="changes.tosChanged" class="space-y-3">
          <h3 class="font-semibold">{{ t('reconsent.tosChanged') }}</h3>

          <template v-if="changes.tosDiff">
            <div class="text-xs text-(--text-muted) mb-1">{{ t('reconsent.whatChanged') }}</div>
            <div class="border border-(--border) rounded-lg p-3 text-sm space-y-1 max-h-48 overflow-y-auto bg-(--bg)">
              <div v-for="(line, i) in parseDiff(changes.tosDiff).removed" :key="'tr-' + i"
                   class="text-error line-through">{{ line }}</div>
              <div v-for="(line, i) in parseDiff(changes.tosDiff).added" :key="'ta-' + i"
                   class="text-success">{{ line }}</div>
            </div>
          </template>

          <button class="text-xs text-primary hover:underline cursor-pointer"
                  @click="showTosFull = !showTosFull">
            {{ showTosFull ? t('reconsent.hideFullText') : t('reconsent.showFullText') }}
          </button>
          <div v-if="showTosFull && changes.tosHtml"
               class="legal-content max-h-96 overflow-y-auto border border-(--border) rounded-lg p-3"
               v-html="changes.tosHtml"/>
        </NeutralContainer>

        <!-- Consent text (always shown) -->
        <InfoContainer class="space-y-2">
          <p class="text-sm">{{ t('reconsent.consentNote') }}</p>
        </InfoContainer>

        <SuccessButton class="w-full" :disabled="submitting" @click="handleAccept">
          {{ submitting ? t('common.loading') : t('reconsent.accept') }}
        </SuccessButton>
      </template>
    </div>
  </div>
</template>
