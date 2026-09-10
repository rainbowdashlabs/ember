/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import IconButton from '@/components/button/IconButton.vue'

/** One entry of a list somebody typed, with the cross that takes it off again. */
defineProps<{
  label: string
}>()

const emit = defineEmits<{
  remove: []
}>()

const {t} = useI18n()
</script>

<template>
  <SecondaryBadge>
    <span class="inline-flex items-center gap-1">
      {{ label }}
      <IconButton :icon="['fas', 'xmark']" :label="t('common.delete')" @click="emit('remove')"/>
    </span>
  </SecondaryBadge>
</template>
