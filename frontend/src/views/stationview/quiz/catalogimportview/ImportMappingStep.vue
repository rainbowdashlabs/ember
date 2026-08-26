/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import CsvColumnMapping from '../csvimportview/CsvColumnMapping.vue'
import ImportStepNav from './ImportStepNav.vue'
import type {CsvMappings} from '@/api/quiz'

const mapping = defineModel<CsvMappings>('mapping', {required: true})

defineProps<{
  headers: string[]
  loading: boolean
}>()

const emit = defineEmits<{
  back: []
  advance: []
}>()

const {t} = useI18n()
</script>

<template>
  <CsvColumnMapping v-model:mapping="mapping" :headers="headers" />
  <ImportStepNav
      :label="t('csvImport.preview')"
      icon="eye"
      :loading="loading"
      @back="emit('back')"
      @advance="emit('advance')"
  />
</template>
