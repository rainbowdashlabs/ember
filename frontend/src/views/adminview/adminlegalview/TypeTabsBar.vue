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
  types: readonly string[]
}>()

const activeType = defineModel<string>({required: true})

function selectType(type: string) {
  activeType.value = type
}
</script>

<template>
  <div class="flex gap-2 flex-wrap">
    <SecondaryButton
        v-for="type in types"
        :key="type"
        :class="{ 'ring-2 ring-primary': activeType === type }"
        @click="selectType(type)"
    >
      {{ t(`adminSettings.legal.${type}`) }}
    </SecondaryButton>
  </div>
</template>
