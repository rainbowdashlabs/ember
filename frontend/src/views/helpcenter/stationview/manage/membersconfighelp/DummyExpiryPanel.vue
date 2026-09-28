/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldValueDisplay from '@/components/display/FieldValueDisplay.vue'
import {FieldTypes} from '@/api/profileFields'
import {expirySettingsOf, type ExpirySettings} from '@/util/expiry'
import ExpiryDateFields from '@/views/stationview/manage/membersconfig/fieldmodal/ExpiryDateFields.vue'

/**
 * An expiry date as the help centre shows it: the settings from the field dialog, and three dates
 * the way the member list writes them, one valid, one running out and one expired.
 */
const {t} = useI18n()

const settings = ref<ExpirySettings>(expirySettingsOf({warnFromDays: 90, reminderDays: [90, 30]}))

function inDays(days: number): string {
  const day = new Date()
  day.setDate(day.getDate() + days)
  return `${day.getFullYear()}-${String(day.getMonth() + 1).padStart(2, '0')}-${String(day.getDate()).padStart(2, '0')}`
}

const examples = [inDays(400), inDays(20), inDays(-12)]
</script>

<template>
  <NeutralContainer class="space-y-4">
    <ExpiryDateFields v-model="settings"/>
    <div class="space-y-1">
      <FieldLabel>{{ t('membersConfig.typeLabels.EXPIRY_DATE') }}</FieldLabel>
      <p v-for="example in examples" :key="example" class="text-sm">
        <FieldValueDisplay :value="example" :field-type="FieldTypes.EXPIRY_DATE" :config="{warnFromDays: 90}"/>
      </p>
    </div>
  </NeutralContainer>
</template>
