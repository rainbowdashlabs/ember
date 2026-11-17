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
  /** Whether this instance pushes, which is when the push switch is shown. */
  pushAvailable?: boolean
}>()

const emit = defineEmits<{
  toggle: []
  togglePush: []
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
    <div v-if="pushAvailable" class="flex items-center justify-between gap-4">
      <div>
        <p class="text-sm font-medium">{{ t('clusterNotifications.pushLabel') }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('clusterNotifications.pushHint') }}</p>
      </div>
      <ToggleInput
          :model-value="settings.pushEnabled"
          :aria-label="t('clusterNotifications.pushLabel')"
          data-testid="cluster-push-toggle"
          @update:model-value="emit('togglePush')"
      />
    </div>
  </NeutralContainer>
</template>
