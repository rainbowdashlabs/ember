/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {adminSettings} from '@/api'
import type {LegalTemplate} from '@/api/adminSettings'

const {t} = useI18n()

const props = defineProps<{
  type: string
  locale: string
}>()

const show = defineModel<boolean>('show', {required: true})

const emit = defineEmits<{
  load: [templates: LegalTemplate[]]
}>()

const templates = ref<LegalTemplate[]>([])
const selected = ref<Set<string>>(new Set())
const loading = ref(false)

async function load() {
  loading.value = true
  templates.value = []
  selected.value = new Set()
  try {
    templates.value = await adminSettings.getLegalTemplates(props.type, props.locale)
  } catch {
    templates.value = []
  } finally {
    loading.value = false
  }
}

function toggle(name: string) {
  const next = new Set(selected.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  selected.value = next
}

function selectAll() {
  selected.value = new Set(templates.value.map(entry => entry.displayName))
}

function confirm() {
  emit('load', templates.value.filter(entry => selected.value.has(entry.displayName)))
  show.value = false
}

watch(show, open => {
  if (open) load()
})
</script>

<template>
  <Modal v-model="show" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('adminSettings.legal.loadTemplateTitle') }}</SubHeader>
      <MutedText size="sm">{{ t('adminSettings.legal.loadTemplateHint') }}</MutedText>

      <Spinner v-if="loading" size="md"/>
      <MutedText v-else-if="templates.length === 0" size="sm">
        {{ t('adminSettings.legal.noTemplates') }}
      </MutedText>

      <div v-else class="space-y-2 max-h-[50vh] overflow-auto">
        <label
            v-for="entry in templates"
            :key="entry.displayName"
            class="flex items-center gap-3 rounded-lg border border-(--border) p-3"
        >
          <ToggleInput :model-value="selected.has(entry.displayName)"
                       :aria-label="entry.displayName"
                       @update:model-value="toggle(entry.displayName)"/>
          <span class="flex-1 min-w-0 truncate font-mono text-sm">{{ entry.displayName }}</span>
        </label>
      </div>

      <div class="flex justify-end gap-2">
        <SecondaryButton v-if="templates.length > 0" @click="selectAll">
          {{ t('adminSettings.legal.selectAllTemplates') }}
        </SecondaryButton>
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="selected.size === 0" @click="confirm">
          {{ t('adminSettings.legal.loadTemplate') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
