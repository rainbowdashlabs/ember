/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ThemeSelector from '@/components/theme/ThemeSelector.vue'
import {useTheme} from '@/composables/useTheme'

const {t} = useI18n()
const themeState = useTheme()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('theme.title') }}</SubHeader>
    <ThemeSelector
      :show-themes="themeState.allowUserTheme.value"
      :show-feel="themeState.allowUserFeel.value"
    />
    <p v-if="!themeState.allowUserTheme.value" class="text-sm text-(--text-muted)">{{ t('theme.controlledByStation') }}</p>
    <p v-if="!themeState.allowUserFeel.value" class="text-sm text-(--text-muted)">{{ t('theme.feelControlledByStation') }}</p>
  </NeutralContainer>
</template>
