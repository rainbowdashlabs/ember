/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {getLoggingConfig, LOG_LEVELS, updateLoggingConfig, type LoggingConfig} from '@/api/applicationLog'

/**
 * What of the log is kept in the database, and for how long.
 *
 * The console always gets everything and is not offered here: the failure the database cannot cover
 * is the database being the thing that broke, so it is never the only place the log goes.
 */
const {t} = useI18n()

const config = ref<LoggingConfig>({databaseEnabled: false, databaseLevel: 'DEBUG', retentionDays: 14})
const error = ref('')
const saved = ref(false)

onMounted(async () => {
  try {
    config.value = await getLoggingConfig()
  } catch {
    error.value = t('common.error')
  }
})

async function save() {
  saved.value = false
  config.value = await updateLoggingConfig(config.value)
  saved.value = true
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminSettings.logging.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('adminSettings.logging.hint') }}</MutedText>

    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="saved" variant="success">{{ t('adminSettings.logging.saved') }}</Alert>

    <div class="flex items-center justify-between gap-3">
      <FieldLabel>{{ t('adminSettings.logging.enabled') }}</FieldLabel>
      <ToggleInput v-model="config.databaseEnabled" :aria-label="t('adminSettings.logging.enabled')"/>
    </div>

    <div class="grid grid-cols-1 md:grid-cols-2 gap-3">
      <div class="space-y-1">
        <FieldLabel>{{ t('adminSettings.logging.level') }}</FieldLabel>
        <SelectInput v-model="config.databaseLevel" :aria-label="t('adminSettings.logging.level')">
          <option v-for="level in LOG_LEVELS" :key="level" :value="level">{{ level }}</option>
        </SelectInput>
        <MutedText tag="p" size="sm">{{ t('adminSettings.logging.levelHint') }}</MutedText>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('adminSettings.logging.retentionDays') }}</FieldLabel>
        <NumberInput
            v-model="config.retentionDays"
            :min="1"
            :max="3650"
            :aria-label="t('adminSettings.logging.retentionDays')"/>
        <MutedText tag="p" size="sm">{{ t('adminSettings.logging.retentionHint') }}</MutedText>
      </div>
    </div>

    <MutedText v-if="config.storedLines !== undefined" tag="p" size="sm">
      {{ t('adminSettings.logging.stored', {count: config.storedLines}) }}
    </MutedText>

    <div class="flex justify-end">
      <SaveButton :action="save"/>
    </div>
  </NeutralContainer>
</template>
