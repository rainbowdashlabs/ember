/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'

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
  <button
      :aria-label="t('quickSearch.openLabel')"
      :title="t('quickSearch.openLabel')"
      class="inline-flex items-center justify-center text-(--text-muted) hover:text-(--text)
             transition-colors cursor-pointer"
      @click="onClick"
  >
    <font-awesome-icon :icon="['fas', 'magnifying-glass']" class="h-4 w-4"/>
  </button>
</template>
