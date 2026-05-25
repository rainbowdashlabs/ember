/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import { adminSettings } from '@/api'

const { t } = useI18n()

const error = ref('')
const success = ref('')

const legalTypes = ['privacy', 'tos', 'consent', 'imprint'] as const
type LegalType = (typeof legalTypes)[number]
const activeLegalTab = ref<LegalType>('privacy')
const legalContent = ref('')
const legalVersion = ref('')
const legalLoading = ref(false)
const legalSaving = ref(false)

async function loadLegalDocument(type: LegalType) {
  legalLoading.value = true
  legalContent.value = ''
  legalVersion.value = ''
  try {
    const doc = await adminSettings.getLegalDocument(type)
    legalContent.value = doc.content
    legalVersion.value = doc.version
  } catch {
    error.value = t('common.error')
  } finally {
    legalLoading.value = false
  }
}

async function saveLegalDocument() {
  legalSaving.value = true
  error.value = ''
  success.value = ''
  try {
    const doc = await adminSettings.updateLegalDocument(activeLegalTab.value, legalContent.value)
    legalVersion.value = doc.version
    success.value = t('adminSettings.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    legalSaving.value = false
  }
}

watch(activeLegalTab, (type) => {
  loadLegalDocument(type)
})

onMounted(async () => {
  await loadLegalDocument(activeLegalTab.value)
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminSettings.legal.title') }}</SectionHeader>

        <div class="flex gap-2 flex-wrap">
          <SecondaryButton
            v-for="type in legalTypes"
            :key="type"
            :class="{ 'ring-2 ring-primary': activeLegalTab === type }"
            @click="activeLegalTab = type"
          >
            {{ t(`adminSettings.legal.${type}`) }}
          </SecondaryButton>
        </div>

        <Spinner v-if="legalLoading" size="md" />

        <template v-if="!legalLoading">
          <div v-if="legalVersion" class="text-xs text-(--text-muted)">
            {{ t('adminSettings.legal.version') }}: {{ legalVersion }}
          </div>
          <MarkdownEditor
            v-model="legalContent"
            :placeholder="t('adminSettings.legal.contentPlaceholder')"
          />
          <div class="flex justify-end">
            <PrimaryButton :disabled="legalSaving" @click="saveLegalDocument">
              {{ t('adminSettings.legal.save') }}
            </PrimaryButton>
          </div>
        </template>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
