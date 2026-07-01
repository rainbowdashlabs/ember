/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  memberName: string
}>()

defineEmits<{
  (e: 'exit'): void
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex items-center justify-between mb-2">
    <SubHeader>{{ memberName }}</SubHeader>
    <SecondaryButton @click="$emit('exit')">
      <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" /> {{ t('protocol.saveAndExit') }}
    </SecondaryButton>
  </div>
</template>
