/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ThemeSelector from '@/components/theme/ThemeSelector.vue'

const lockTheme = defineModel<boolean>('lockTheme', {required: true})
const lockFeel = defineModel<boolean>('lockFeel', {required: true})

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('theme.stationTheme') }}</SectionHeader>
    <p class="text-xs text-(--text-muted)">{{ t('theme.stationDefaultThemeHint') }}</p>
    <ThemeSelector
        v-model:lock-theme="lockTheme"
        v-model:lock-feel="lockFeel"
        :show-lock-theme="true"
        :show-lock-feel="true"
    />
  </NeutralContainer>
</template>
