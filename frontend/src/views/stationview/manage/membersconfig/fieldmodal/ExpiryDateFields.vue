/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import type {ExpirySettings} from '@/util/expiry'
import ReminderDaysInput from './ReminderDaysInput.vue'
import SettingToggle from './SettingToggle.vue'

/**
 * What an expiry date does beyond holding a date: how early it shows as running out, when it
 * reminds, whether it keeps reminding once it has passed, and whom it reminds.
 */
const settings = defineModel<ExpirySettings>({required: true})

const {t} = useI18n()

/** The default gap a repeat starts from when it is switched on. */
const FIRST_REPEAT_DAYS = 7

function patch(change: Partial<ExpirySettings>) {
  settings.value = {...settings.value, ...change}
}

function wholeDays(value: number | undefined, least: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(least, Math.round(value)) : least
}

const warnFromDays = computed<number | undefined>({
  get: () => settings.value.warnFromDays,
  set: value => patch({warnFromDays: wholeDays(value, 0)}),
})
const reminderDays = computed({
  get: () => settings.value.reminderDays,
  set: value => patch({reminderDays: value}),
})
const repeats = computed({
  get: () => settings.value.repeatEveryDays !== null,
  set: on => patch({repeatEveryDays: on ? FIRST_REPEAT_DAYS : null}),
})
const repeatEveryDays = computed<number | undefined>({
  get: () => settings.value.repeatEveryDays ?? FIRST_REPEAT_DAYS,
  set: value => patch({repeatEveryDays: wholeDays(value, 1)}),
})
const remindMember = computed({
  get: () => settings.value.remindMember,
  set: value => patch({remindMember: value}),
})
const remindManagement = computed({
  get: () => settings.value.remindManagement,
  set: value => patch({remindManagement: value}),
})
</script>

<template>
  <div class="space-y-4" data-testid="expiry-settings">
    <LabelledField :label="t('membersConfig.expiry.warnFrom')" :help="t('membersConfig.expiry.warnFromHint')">
      <NumberInput v-model="warnFromDays" class="w-28" data-testid="expiry-warn-from"/>
    </LabelledField>
    <LabelledField :label="t('membersConfig.expiry.reminders')" :help="t('membersConfig.expiry.remindersHint')">
      <ReminderDaysInput v-model="reminderDays"/>
    </LabelledField>
    <div class="space-y-2">
      <SettingToggle v-model="repeats" :label="t('membersConfig.expiry.repeat')"
                     :hint="t('membersConfig.expiry.repeatHint')"/>
      <LabelledField v-if="repeats" :label="t('membersConfig.expiry.repeatEvery')">
        <NumberInput v-model="repeatEveryDays" class="w-28" data-testid="expiry-repeat-every"/>
      </LabelledField>
    </div>
    <SettingToggle v-model="remindMember" :label="t('membersConfig.expiry.remindMember')"
                   :hint="t('membersConfig.expiry.remindMemberHint')"/>
    <SettingToggle v-model="remindManagement" :label="t('membersConfig.expiry.remindManagement')"
                   :hint="t('membersConfig.expiry.remindManagementHint')"/>
  </div>
</template>
