/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {OPTIONAL_NECESSITIES, type StorageNecessityName} from '@/api/storage'

/**
 * The choice a member has over what stays in their browser: the technically required values come
 * with the consent itself, the two optional groups are switched here.
 */
const scopes = defineModel<StorageNecessityName[]>({required: true})

const {t} = useI18n()

function toggle(scope: StorageNecessityName, allowed: boolean) {
  scopes.value = allowed
      ? [...scopes.value, scope]
      : scopes.value.filter(entry => entry !== scope)
}
</script>

<template>
  <div class="space-y-3">
    <div class="rounded-theme border border-(--border) p-3">
      <div class="flex items-center justify-between gap-3">
        <FieldLabel>{{ t('storageConsent.scope.REQUIRED.title') }}</FieldLabel>
        <span class="shrink-0 text-xs text-(--text-muted)">{{ t('storageConsent.scope.always') }}</span>
      </div>
      <p class="mt-1 text-xs text-(--text-muted)">{{ t('storageConsent.scope.REQUIRED.hint') }}</p>
    </div>

    <div v-for="scope in OPTIONAL_NECESSITIES" :key="scope" class="rounded-theme border border-(--border) p-3">
      <div class="flex items-center justify-between gap-3">
        <FieldLabel>{{ t(`storageConsent.scope.${scope}.title`) }}</FieldLabel>
        <ToggleInput :model-value="scopes.includes(scope)"
                     :aria-label="t(`storageConsent.scope.${scope}.title`)"
                     @update:model-value="value => toggle(scope, value)"/>
      </div>
      <p class="mt-1 text-xs text-(--text-muted)">{{ t(`storageConsent.scope.${scope}.hint`) }}</p>
    </div>
  </div>
</template>
