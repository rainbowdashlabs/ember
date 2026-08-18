/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {adminSettings} from '@/api'
import type {DocumentPlaceholder} from '@/api/adminSettings'

const {t} = useI18n()

const placeholders = defineModel<DocumentPlaceholder[]>('placeholders', {required: true})

const emit = defineEmits<{
  error: [message: string]
  saved: []
}>()

const loading = ref(false)
const expanded = ref(false)

const missing = computed(() => placeholders.value.filter(entry => !entry.value.trim()).length)

/** The token as it is written in a document. Built here because the braces confuse the template parser. */
function token(name: string): string {
  return `{{ ${name} }}`
}

function usageLabel(entry: DocumentPlaceholder): string {
  if (entry.usages.length === 0) return t('adminSettings.legal.placeholderUnused')
  const documents = [...new Set(entry.usages.map(usage => t(`adminSettings.legal.${usage.type}`)))]
  return documents.join(', ')
}

function setValue(name: string, value: string) {
  placeholders.value = placeholders.value.map(entry => (entry.name === name ? {...entry, value} : entry))
}

async function save() {
  const values: Record<string, string> = {}
  for (const entry of placeholders.value) values[entry.name] = entry.value
  try {
    placeholders.value = await adminSettings.saveLegalPlaceholders(values)
    emit('saved')
  } catch (e) {
    emit('error', t('common.error'))
    throw e
  }
}

async function load() {
  loading.value = true
  try {
    placeholders.value = await adminSettings.getLegalPlaceholders()
  } catch {
    emit('error', t('common.error'))
  } finally {
    loading.value = false
  }
}

defineExpose({reload: load})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center gap-3">
      <SectionHeader class="flex-1 min-w-0">{{ t('adminSettings.legal.placeholderTitle') }}</SectionHeader>
      <ErrorBadge v-if="missing > 0">{{ t('adminSettings.legal.placeholderMissing', {count: missing}) }}</ErrorBadge>
      <SecondaryButton :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']" @click="expanded = !expanded">
        {{ expanded ? t('adminSettings.legal.placeholderHide') : t('adminSettings.legal.placeholderShow') }}
      </SecondaryButton>
    </div>

    <MutedText size="sm">{{ t('adminSettings.legal.placeholderHint') }}</MutedText>

    <template v-if="expanded">
      <Spinner v-if="loading" size="md"/>
      <MutedText v-else-if="placeholders.length === 0" size="sm">
        {{ t('adminSettings.legal.placeholderNone') }}
      </MutedText>

      <div v-else class="space-y-3">
        <div
            v-for="entry in placeholders"
            :key="entry.name"
            class="grid gap-2 sm:grid-cols-[minmax(0,1fr)_minmax(0,2fr)] sm:items-center"
        >
          <div class="min-w-0">
            <code class="text-sm break-all">{{ token(entry.name) }}</code>
            <MutedText size="sm" class="block">{{ usageLabel(entry) }}</MutedText>
          </div>
          <TextInput
              :model-value="entry.value"
              :aria-label="entry.name"
              :placeholder="t('adminSettings.legal.placeholderValuePlaceholder')"
              @update:model-value="v => setValue(entry.name, v ?? '')"
          />
        </div>

        <div class="flex justify-end">
          <SaveButton :action="save"/>
        </div>
      </div>
    </template>
  </NeutralContainer>
</template>
