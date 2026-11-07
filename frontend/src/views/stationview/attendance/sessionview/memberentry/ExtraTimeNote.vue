/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {extraTimeText} from '@/components/attendance/timerules/extraTimeText'
import {useSheetExtraTime} from '../useSheetExtraTime'

/**
 * The preparation and follow-up a present person is credited with, under their name, with the switch
 * for an evening they did not do it. Says nothing where the sheet's rules do not name them.
 */
const props = defineProps<{
  entryId: number
}>()

const {t} = useI18n()
const sheet = useSheetExtraTime()

const credit = computed(() => sheet?.credits.value.get(props.entryId) ?? null)
const text = computed(() => credit.value
    ? extraTimeText(t, credit.value.preparationMinutes, credit.value.followUpMinutes)
    : '')
const busy = computed(() => sheet?.busyEntryId.value === props.entryId)
const failure = computed(() => sheet?.switchFailure.value?.entryId === props.entryId
    ? sheet.switchFailure.value.failure
    : null)
</script>

<template>
  <div v-if="sheet && credit" class="mt-1 flex flex-wrap items-center gap-2 text-xs" data-testid="extra-time-note">
    <span :class="credit.skipped ? 'text-(--text-muted) line-through' : 'text-(--text-muted)'">{{ text }}</span>
    <span v-if="credit.skipped" class="text-(--text-muted)">{{ t('attendanceTimeRules.skipped') }}</span>
    <BareButton
        v-if="sheet.canEdit.value"
        :aria-pressed="credit.skipped"
        :aria-label="t('attendanceTimeRules.didNotDoItLabel', {time: text})"
        :disabled="busy"
        class="text-primary hover:underline disabled:opacity-50"
        data-testid="extra-time-toggle"
        @click="sheet.setSkipped(entryId, !credit.skipped)"
    >
      {{ credit.skipped ? t('attendanceTimeRules.didIt') : t('attendanceTimeRules.didNotDoIt') }}
    </BareButton>
    <FailureAlert :failure="failure" class="w-full"/>
  </div>
</template>
