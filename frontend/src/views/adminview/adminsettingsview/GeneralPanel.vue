/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()

defineProps<{
  registrationEnabled: boolean
  availableMailLocales: string[]
}>()

const forcePrideFlag = defineModel<boolean>('forcePrideFlag', {required: true})
const defaultMailLocale = defineModel<string>('defaultMailLocale', {required: true})

const emit = defineEmits<{
  'toggle-registration': [value: boolean]
  'save-pride': []
  'save-mail-locale': []
}>()

function onPrideToggle(value: boolean) {
  forcePrideFlag.value = value
  emit('save-pride')
}

/**
 * The language names the application already knows, so the picker reads "Deutsch" rather than "de".
 * A language the instance ships templates for but has no name for falls back to its code.
 */
function localeLabel(locale: string): string {
  const key = `adminSettings.mailLocaleNames.${locale}`
  const label = t(key)
  return label === key ? locale : label
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('adminSettings.title') }}</SectionHeader>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.stationRegistration') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.stationRegistrationHint') }}</div>
      </div>
      <ToggleInput data-onboarding="admin.settings.station-registration" :model-value="registrationEnabled"
                   :aria-label="t('adminSettings.stationRegistration')"
                   @update:model-value="(v: boolean) => emit('toggle-registration', v)"/>
    </div>

    <div class="flex items-center justify-between">
      <div>
        <div class="font-medium">{{ t('adminSettings.forcePrideFlag') }}</div>
        <div class="text-sm text-(--text-muted)">{{ t('adminSettings.forcePrideFlagHint') }}</div>
      </div>
      <ToggleInput :model-value="forcePrideFlag" :aria-label="t('adminSettings.forcePrideFlag')"
                   @update:model-value="onPrideToggle"/>
    </div>

    <div class="space-y-1 border-t border-(--border) pt-4">
      <FieldLabel>{{ t('adminSettings.mailLocale') }}</FieldLabel>
      <SelectInput v-model="defaultMailLocale" @change="emit('save-mail-locale')">
        <option v-for="locale in availableMailLocales" :key="locale" :value="locale">
          {{ localeLabel(locale) }}
        </option>
      </SelectInput>
      <MutedText tag="p" size="sm">{{ t('adminSettings.mailLocaleHint') }}</MutedText>
    </div>
  </NeutralContainer>
</template>
