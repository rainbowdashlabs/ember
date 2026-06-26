/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

defineProps<{
  locales: string[]
}>()

const activeLocale = defineModel<string>({required: true})

const emit = defineEmits<{
  add: []
}>()
</script>

<template>
  <div class="flex items-center gap-2 flex-wrap">
    <SecondaryButton
        v-for="locale in locales"
        :key="locale"
        :class="{ 'ring-2 ring-secondary': activeLocale === locale }"
        @click="activeLocale = locale"
    >
      {{ locale.toUpperCase() }}
    </SecondaryButton>
    <SecondaryButton :icon="['fas', 'plus']" @click="emit('add')">
      {{ t('adminSettings.legal.addLocale') }}
    </SecondaryButton>
  </div>
</template>
