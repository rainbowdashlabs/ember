/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useTheme} from '@/composables/useTheme'
import ThemePicker from '@/components/theme/ThemePicker.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import {DarkMode, Feel, THEMES} from '@/theme/themes'

const props = withDefaults(defineProps<{
  showThemes?: boolean
  showFeel?: boolean
  showLockTheme?: boolean
  showLockFeel?: boolean
}>(), {
  showThemes: true,
  showFeel: true,
  showLockTheme: false,
  showLockFeel: false,
})

const lockTheme = defineModel<boolean>('lockTheme')
const lockFeel = defineModel<boolean>('lockFeel')

const {t} = useI18n()
const themeState = useTheme()

const availableFeels = computed(() => {
  const theme = THEMES[themeState.activeTheme.value]
  return theme ? theme.supportedFeels : [Feel.ROUNDED, Feel.CORNERS]
})
</script>

<template>
  <div class="space-y-3">
    <!-- Dark mode -->
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

    <!-- Theme picker -->
    <template v-if="showThemes">
      <ThemePicker />
      <div v-if="props.showLockTheme" class="flex items-center justify-between">
        <span class="text-sm text-(--text-muted)">{{ t('theme.allowUserTheme') }}</span>
        <ToggleInput :model-value="!lockTheme" @update:model-value="lockTheme = !$event"/>
      </div>
    </template>

    <!-- Feel -->
    <template v-if="showFeel && availableFeels.length > 1">
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
      <div v-if="props.showLockFeel" class="flex items-center justify-between">
        <span class="text-sm text-(--text-muted)">{{ t('theme.allowUserFeel') }}</span>
        <ToggleInput :model-value="!lockFeel" @update:model-value="lockFeel = !$event"/>
      </div>
    </template>
  </div>
</template>
