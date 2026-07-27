/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

const props = defineProps<{
  scope?: string
}>()

const emit = defineEmits<{
  open: [scope: string]
}>()

const {t} = useI18n()
const scope = computed(() => props.scope ?? 'station')

function onClick() {
  emit('open', scope.value)
}
</script>

<template>
  <MutedIconButton
      :icon="['fas', 'magnifying-glass']"
      :label="t('quickSearch.openLabel')"
      hover="text"
      @click="onClick"
  />
</template>
