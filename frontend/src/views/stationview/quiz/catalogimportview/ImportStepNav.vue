/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  label: string
  icon: string
  loading: boolean
}>()

const emit = defineEmits<{
  back: []
  advance: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex justify-between gap-3">
    <SecondaryButton @click="emit('back')">{{ t('common.back') }}</SecondaryButton>
    <PrimaryButton :disabled="loading" :icon="['fas', icon]" @click="emit('advance')">
      {{ loading ? t('common.loading') : label }}
    </PrimaryButton>
  </div>
</template>
