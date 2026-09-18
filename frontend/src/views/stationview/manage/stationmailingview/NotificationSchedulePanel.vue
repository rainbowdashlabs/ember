/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TimeShortInput from '@/components/input/datetime/TimeShortInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import Alert from '@/components/feedback/Alert.vue'
import {getNotificationSchedule, saveNotificationSchedule} from '@/api/mailProviders'

/**
 * When this station's gathered notifications are mailed out.
 *
 * <p>A station says the moments rather than a length of time. Seven in the morning means seven in the
 * morning, where "no more often than every six hours" meant whenever the server was last restarted.
 *
 * <p>Two ways of saying it, because that is how somebody thinks about it: a few times of day, or
 * simply every hour. They are one thing underneath, every hour being the list that holds all
 * twenty-four, so nothing here has to know which was chosen once it is written down.
 */
const {t} = useI18n()

type Mode = 'operator' | 'times' | 'hourly'

const mode = ref<Mode>('operator')
const times = ref<string[]>([])
const floorMinutes = ref(60)
const loading = ref(true)
const saving = ref(false)
const saved = ref(false)
const failed = ref(false)

const HOURLY = Array.from({length: 24}, (_, hour) => `${String(hour).padStart(2, '0')}:00`)

function isHourly(list: string[]): boolean {
    return list.length === 24 && HOURLY.every(hour => list.includes(hour))
}

onMounted(async () => {
    try {
        const schedule = await getNotificationSchedule()
        floorMinutes.value = schedule.floorMinutes
        const stored = schedule.sendTimes.map(time => time.slice(0, 5))
        if (stored.length === 0) mode.value = 'operator'
        else if (isHourly(stored)) mode.value = 'hourly'
        else {
            mode.value = 'times'
            times.value = stored
        }
    } catch {
        failed.value = true
    }
    loading.value = false
})

/**
 * Whether the station is asking to be written to more often than the installation allows.
 *
 * <p>Said out loud rather than silently ignored: a station that asks for hourly mail on an
 * installation whose operator allows one mail every six hours would otherwise believe it had it.
 */
const askingTooOften = computed(() => {
    if (mode.value === 'hourly') return floorMinutes.value > 60
    if (mode.value !== 'times' || times.value.length < 2) return false
    return floorMinutes.value > 60
})

function addTime() {
    times.value = [...times.value, '07:00']
}

function removeTime(index: number) {
    times.value = times.value.filter((_, at) => at !== index)
}

function chosenTimes(): string[] {
    if (mode.value === 'operator') return []
    if (mode.value === 'hourly') return HOURLY
    return [...new Set(times.value.filter(Boolean))].sort()
}

async function save() {
    saving.value = true
    saved.value = false
    failed.value = false
    try {
        await saveNotificationSchedule(chosenTimes())
        saved.value = true
    } catch {
        failed.value = true
    }
    saving.value = false
}
</script>

<template>
  <NeutralContainer v-if="!loading" class="space-y-3" data-testid="notification-schedule">
    <SectionHeader>{{ t('notificationSchedule.title') }}</SectionHeader>
    <MutedText size="sm">{{ t('notificationSchedule.hint') }}</MutedText>

    <div class="space-y-1">
      <FieldLabel>{{ t('notificationSchedule.mode') }}</FieldLabel>
      <SelectInput v-model="mode" class="w-full sm:w-auto" data-testid="notification-schedule-mode">
        <option value="operator">{{ t('notificationSchedule.modeOperator') }}</option>
        <option value="times">{{ t('notificationSchedule.modeTimes') }}</option>
        <option value="hourly">{{ t('notificationSchedule.modeHourly') }}</option>
      </SelectInput>
    </div>

    <div v-if="mode === 'times'" class="space-y-2">
      <FieldLabel>{{ t('notificationSchedule.times') }}</FieldLabel>
      <div v-for="(time, index) in times" :key="index" class="flex items-center gap-2">
        <TimeShortInput :model-value="time" class="w-28" @update:model-value="times[index] = String($event)"/>
        <DeleteButton @click="removeTime(index)"/>
      </div>
      <SecondaryButton :icon="['fas', 'plus']" @click="addTime">
        {{ t('notificationSchedule.addTime') }}
      </SecondaryButton>
    </div>

    <Alert v-if="askingTooOften" variant="info">{{ t('notificationSchedule.floorHint', {minutes: floorMinutes}) }}</Alert>
    <Alert v-if="saved" variant="success">{{ t('notificationSchedule.saved') }}</Alert>
    <Alert v-if="failed" variant="error">{{ t('common.error') }}</Alert>

    <ButtonRow>
      <PrimaryButton :disabled="saving" @click="save">{{ t('common.save') }}</PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
