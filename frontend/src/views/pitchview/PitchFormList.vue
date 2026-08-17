/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ManagedFormsSection from '@/views/stationview/forms/listview/ManagedFormsSection.vue'
import type {Form} from '@/api/forms'

/** The forms of a station, drawn by the application's own tile section. */
defineProps<{
  forms: Form[]
}>()

const {t} = useI18n()

const STATUS = {DRAFT: 'forms.statusDraft', OPEN: 'forms.statusOpen', CLOSED: 'forms.statusClosed'}

const statusLabel = (status: string) => t(STATUS[status as keyof typeof STATUS] ?? 'forms.statusDraft')
</script>

<template>
  <ManagedFormsSection :forms="forms" :can-create-polls="true" :status-label="statusLabel"/>
</template>
