/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

defineProps<{
  checkMode: boolean
  uncheckedCount: number
}>()

defineEmits<{
  back: []
  export: []
  sync: []
  startCheckMode: []
}>()
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="$emit('back')">
      {{ t('attendanceSession.back') }}
    </SecondaryButton>
    <div class="grid grid-cols-2 sm:flex sm:items-center gap-2">
      <SecondaryButton :icon="['fas', 'download']" @click="$emit('export')">
        {{ t('attendanceSession.export') }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'clipboard-check']" @click="$emit('sync')">
        {{ t('attendanceSession.sync') }}
      </SecondaryButton>
      <PrimaryButton
          v-if="!checkMode && uncheckedCount > 0"
          :icon="['fas', 'clipboard-user']"
          class="col-span-2"
          @click="$emit('startCheckMode')"
      >
        {{ t('attendanceSession.checkMode') }} ({{ uncheckedCount }})
      </PrimaryButton>
    </div>
  </div>
</template>
