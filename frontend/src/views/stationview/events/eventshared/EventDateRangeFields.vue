/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DateInput from '@/components/input/datetime/DateInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

/**
 * The two dates a list of appointments is cut down to, either of which may be left empty.
 *
 * <p>Two fields rather than one, and no preset ranges: the question a reader actually arrives with
 * is "the drills last autumn", which is a pair of dates and not a number of months back.
 */
const from = defineModel<string>('from', {required: true})
const to = defineModel<string>('to', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="w-40 space-y-1">
    <FieldLabel>{{ t('events.dateFrom') }}</FieldLabel>
    <DateInput v-model="from"/>
  </div>
  <div class="w-40 space-y-1">
    <FieldLabel>{{ t('events.dateTo') }}</FieldLabel>
    <DateInput v-model="to"/>
  </div>
</template>
