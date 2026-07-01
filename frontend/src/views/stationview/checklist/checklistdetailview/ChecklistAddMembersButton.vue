/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

defineEmits<{
  (e: 'click'): void
}>()
</script>

<template>
  <SecondaryButton @click="$emit('click')">
    <font-awesome-icon :icon="['fas', 'user-plus']" class="mr-1"/>
    {{ t('checklist.addMembers') }}
  </SecondaryButton>
</template>
