/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Spinner from '@/components/feedback/Spinner.vue'

/**
 * The progress of a save that happens without a button, on a setting kept as soon as it is changed:
 * saving while it is written, saved for a moment after, and nothing otherwise. Where there is a
 * button, the button shows this itself.
 */
defineProps<{
  saving: boolean
  saved: boolean
}>()

const {t} = useI18n()
</script>

<template>
  <Transition name="fade" mode="out-in">
    <span v-if="saving" key="saving" class="text-xs text-(--text-muted) flex items-center gap-1">
      <Spinner size="sm"/> {{ t('common.saving') }}
    </span>
    <span v-else-if="saved" key="saved" class="text-xs text-success flex items-center gap-1">
      <font-awesome-icon :icon="['fas', 'check']"/> {{ t('common.saved') }}
    </span>
  </Transition>
</template>
