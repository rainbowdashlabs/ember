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

const STATE = {
  NOT_PUBLISHED: 'forms.statusDraft',
  OPEN: 'forms.statusOpen',
  NOT_OPEN_YET: 'forms.statusScheduled',
  CLOSED: 'forms.statusClosed',
}

const statusLabel = (state: string) => t(STATE[state as keyof typeof STATE] ?? 'forms.statusDraft')

/** The pitch shows the tiles rather than offering them, so none of them opens anything. */
const noPage = () => null
</script>

<template>
  <ManagedFormsSection :forms="forms" :can-create-polls="true" :status-label="statusLabel" :form-page="noPage"/>
</template>
