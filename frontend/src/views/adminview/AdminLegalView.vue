/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, onMounted, watch, useTemplateRef} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Alert from '@/components/feedback/Alert.vue'
import TypeTabsBar from './adminlegalview/TypeTabsBar.vue'
import LocaleTabsBar from './adminlegalview/LocaleTabsBar.vue'
import LegalFileEditor from './adminlegalview/LegalFileEditor.vue'
import LegalPlaceholderPanel from './adminlegalview/LegalPlaceholderPanel.vue'
import SingleFieldModal from '@/components/feedback/SingleFieldModal.vue'
import {adminSettings} from '@/api'
import type {DocumentPlaceholder} from '@/api/adminSettings'

const {t} = useI18n()

const error = ref('')

const legalTypes = ['privacy', 'tos', 'consent', 'imprint'] as const
type LegalType = (typeof legalTypes)[number]
const activeLegalTab = ref<LegalType>('privacy')
const activeLocale = ref('de')
const locales = ref<string[]>([])

const showAddLocaleModal = ref(false)
const newLocaleCode = ref('')

const editor = useTemplateRef<InstanceType<typeof LegalFileEditor>>('editor')
const placeholderPanel = useTemplateRef<InstanceType<typeof LegalPlaceholderPanel>>('placeholderPanel')

const placeholders = ref<DocumentPlaceholder[]>([])

const placeholderValues = computed(() => {
  const values: Record<string, string> = {}
  for (const entry of placeholders.value) values[entry.name] = entry.value
  return values
})

async function loadLocales(type: LegalType) {
  try {
    const result = await adminSettings.getLegalLocales(type)
    locales.value = Array.isArray(result) && result.length > 0 ? result : ['de']
    if (!locales.value.includes(activeLocale.value)) {
      activeLocale.value = locales.value[0] ?? 'de'
    }
  } catch {
    locales.value = ['de']
  }
}

async function addLocale() {
  const code = newLocaleCode.value.trim().toLowerCase()
  if (!code || locales.value.includes(code)) return
  showAddLocaleModal.value = false
  newLocaleCode.value = ''
  try {
    await adminSettings.saveLegalFiles(activeLegalTab.value, code, [{
      filename: '', displayName: 'content', content: '', enabled: true,
    }])
    await loadLocales(activeLegalTab.value)
    activeLocale.value = code
    await editor.value?.reload()
  } catch {
    error.value = t('common.error')
  }
}

watch(activeLegalTab, loadLocales)

onMounted(async () => {
  await loadLocales(activeLegalTab.value)
  await placeholderPanel.value?.reload()
})
</script>

<template>
  <ViewContent :title="t('pages.admin-legal.title')" :subtitle="t('pages.admin-legal.subtitle')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer class="space-y-4">
        <TypeTabsBar v-model="activeLegalTab" :types="legalTypes"/>
        <LocaleTabsBar
            v-model="activeLocale"
            :locales="locales"
            @add="showAddLocaleModal = true"
        />
        <LegalFileEditor
            ref="editor"
            :type="activeLegalTab"
            :locale="activeLocale"
            :placeholder-values="placeholderValues"
            @error="error = $event"
            @saved="placeholderPanel?.reload()"
        />
      </NeutralContainer>

      <LegalPlaceholderPanel
          ref="placeholderPanel"
          v-model:placeholders="placeholders"
          @error="error = $event"
          @saved="editor?.reload()"
      />

      <SingleFieldModal
          v-model:show="showAddLocaleModal"
          v-model:value="newLocaleCode"
          :title="t('adminSettings.legal.addLocaleTitle')"
          :placeholder="t('adminSettings.legal.localeCodePlaceholder')"
          :confirm-label="t('adminSettings.legal.addLocale')"
          @confirm="addLocale"
      />
    </div>
  </ViewContent>
</template>
