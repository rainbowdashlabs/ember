/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {inventory} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {apiErrorMessage} from '@/util/apiError'

/**
 * What the station asks of a member who cannot find their gear.
 *
 * <p>A member reports their own loss without anybody granting them anything, so the only thing left to
 * decide is whether they have to say what happened. Stations that have to make sense of a loss
 * afterwards want the sentence; ones that do not should not make people invent one.
 */
const {t} = useI18n()

const noteRequired = ref(false)
const saveError = ref('')

const {loading, error} = useAsyncLoader(async () => {
  noteRequired.value = (await inventory.getSettings()).lossNoteRequired
})

let loaded = false
watch(loading, value => {
  if (!value) loaded = true
})

watch(noteRequired, async (value, previous) => {
  if (!loaded || value === previous) return
  saveError.value = ''
  try {
    await inventory.updateSettings({lossNoteRequired: value})
  } catch (e) {
    noteRequired.value = previous
    saveError.value = apiErrorMessage(e) ?? t('common.error')
  }
})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="loss-settings">
    <SectionHeader>{{ t('inventory.lossSettings.title') }}</SectionHeader>
    <MutedText size="sm">{{ t('inventory.lossSettings.description') }}</MutedText>

    <Spinner v-if="loading" size="sm"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="saveError" variant="error">{{ saveError }}</Alert>

    <div v-if="!loading" class="flex items-center gap-3">
      <ToggleInput v-model="noteRequired" data-testid="loss-note-required"/>
      <span class="text-sm">{{ t('inventory.lossSettings.noteRequired') }}</span>
    </div>
  </NeutralContainer>
</template>
