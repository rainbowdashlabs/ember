/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import type {ClusterMail} from '@/api/generated/schema'

defineProps<{
  settings: ClusterMail
}>()

const emit = defineEmits<{
  toggle: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader class="text-sm">{{ t('clusterNotifications.mailTitle') }}</SubHeader>
    <div class="flex items-center justify-between gap-4">
      <div>
        <p class="text-sm font-medium">{{ t('clusterNotifications.mailLabel') }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('clusterNotifications.mailHint') }}</p>
        <p v-if="!settings.mailAvailable" class="text-xs text-(--text-muted)">
          {{ t('clusterNotifications.mailUnavailable') }}
        </p>
      </div>
      <ToggleInput
          :model-value="settings.emailEnabled"
          :disabled="!settings.mailAvailable"
          :aria-label="t('clusterNotifications.mailLabel')"
          @update:model-value="emit('toggle')"
      />
    </div>
  </NeutralContainer>
</template>
