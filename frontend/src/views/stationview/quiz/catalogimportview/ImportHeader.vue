/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

defineProps<{
  leaveLabel: string
  appending: boolean
}>()

const emit = defineEmits<{
  leave: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex items-center gap-2">
    <SecondaryButton :icon="['fas', 'chevron-left']" @click="emit('leave')">{{ leaveLabel }}</SecondaryButton>
    <SubHeader>{{ appending ? t('quiz.import.appendTitle') : t('quiz.import.createTitle') }}</SubHeader>
  </div>
</template>
