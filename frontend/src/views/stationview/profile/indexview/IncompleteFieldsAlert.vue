/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import type {LaidOutField} from '@/components/profilefields/ProfileFieldsLayout.vue'
import {profileKey} from '@/util/profileFields'

defineProps<{
  incompleteFields: LaidOutField[]
}>()

const { t } = useI18n()
</script>

<template>
  <ErrorContainer v-if="incompleteFields.length > 0" class="space-y-2">
    <p class="font-semibold text-sm">{{ t('profile.incompleteTitle') }}</p>
    <p class="text-xs">{{ t('profile.incompleteHint') }}</p>
    <ul class="list-disc list-inside text-sm space-y-0.5">
      <li v-for="f in incompleteFields" :key="profileKey(f.id, f.origin ?? 'STATION')">{{ f.name }}</li>
    </ul>
  </ErrorContainer>
</template>
