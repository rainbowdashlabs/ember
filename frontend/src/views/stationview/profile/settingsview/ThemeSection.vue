/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {useTheme} from '@/composables/useTheme'
import ThemePicker from '@/components/theme/ThemePicker.vue'
import {DarkMode, Feel, THEMES} from '@/theme/themes'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import {computed} from 'vue'

const {t} = useI18n()
const themeState = useTheme()

const availableFeels = computed(() => {
  const theme = THEMES[themeState.activeTheme.value]
  return theme ? theme.supportedFeels : [Feel.ROUNDED, Feel.CORNERS]
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('theme.title') }}</SubHeader>
    <div class="space-y-3">
      <div class="flex items-center gap-2">
        <span class="text-sm font-medium">{{ t('theme.darkMode') }}</span>
        <div class="flex gap-1">
          <SelectionToggleButton
            v-for="mode in [DarkMode.SYSTEM, DarkMode.LIGHT, DarkMode.DARK]"
            :key="mode"
            :selected="themeState.darkMode.value === mode"
            @toggle="themeState.setDarkMode(mode)"
          >
            {{ mode === 'system' ? t('theme.darkModeSystem') : mode === 'light' ? t('theme.darkModeLight') : t('theme.darkModeDark') }}
          </SelectionToggleButton>
        </div>
      </div>
      <template v-if="themeState.allowUserTheme.value">
        <p class="text-sm text-(--text-muted)">{{ t('theme.selectThemeHint') }}</p>
        <ThemePicker />
      </template>
      <p v-else class="text-sm text-(--text-muted)">{{ t('theme.controlledByStation') }}</p>

      <template v-if="themeState.allowUserFeel.value && availableFeels.length > 1">
        <div class="flex items-center gap-2">
          <span class="text-sm font-medium">{{ t('theme.feel') }}</span>
          <div class="flex gap-1">
            <SelectionToggleButton
              v-for="feel in availableFeels"
              :key="feel"
              :selected="themeState.activeFeel.value === feel"
              @toggle="themeState.setFeel(feel)"
            >
              {{ t(`theme.feel${feel}`) }}
            </SelectionToggleButton>
          </div>
        </div>
      </template>
      <p v-else-if="!themeState.allowUserFeel.value" class="text-sm text-(--text-muted)">{{ t('theme.feelControlledByStation') }}</p>
    </div>
  </NeutralContainer>
</template>
