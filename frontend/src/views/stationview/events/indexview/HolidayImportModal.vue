/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const emit = defineEmits<{
  import: [holidays: Array<{ name: string; startDate: string; endDate: string }>]
}>()

const holidayState = ref('NW')
const holidayYear = ref(String(new Date().getFullYear()))
const loading = ref(false)
const error = ref('')

const germanStates = [
  {code: 'BW', name: 'Baden-Württemberg'},
  {code: 'BY', name: 'Bayern'},
  {code: 'BE', name: 'Berlin'},
  {code: 'BB', name: 'Brandenburg'},
  {code: 'HB', name: 'Bremen'},
  {code: 'HH', name: 'Hamburg'},
  {code: 'HE', name: 'Hessen'},
  {code: 'MV', name: 'Mecklenburg-Vorpommern'},
  {code: 'NI', name: 'Niedersachsen'},
  {code: 'NW', name: 'Nordrhein-Westfalen'},
  {code: 'RP', name: 'Rheinland-Pfalz'},
  {code: 'SL', name: 'Saarland'},
  {code: 'SN', name: 'Sachsen'},
  {code: 'ST', name: 'Sachsen-Anhalt'},
  {code: 'SH', name: 'Schleswig-Holstein'},
  {code: 'TH', name: 'Thüringen'},
]

async function importHolidays() {
  loading.value = true
  error.value = ''
  try {
    const res = await fetch(`https://deutsche-schulferien-api.vercel.app/api/v2/${holidayYear.value}?states=${holidayState.value}`)
    if (!res.ok) throw new Error('Failed to fetch holidays')
    const data: Array<{ name_cp: string; start: string; end: string }> = await res.json()
    const holidays = data.map(h => ({
      name: h.name_cp,
      startDate: h.start.slice(0, 10),
      endDate: h.end.slice(0, 10),
    }))
    emit('import', holidays)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SectionHeader>{{ t('events.importHolidaysTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('events.importHolidaysHint') }}</p>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.holidayState') }}</FieldLabel>
        <SelectInput v-model="holidayState">
          <option v-for="state in germanStates" :key="state.code" :value="state.code">{{ state.name }}</option>
        </SelectInput>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.holidayYear') }}</FieldLabel>
        <SelectInput v-model="holidayYear">
          <option v-for="y in [2024, 2025, 2026, 2027, 2028]" :key="y" :value="String(y)">{{ y }}</option>
        </SelectInput>
      </div>

      <p v-if="error" class="text-sm text-error">{{ error }}</p>

      <div class="flex justify-end gap-3">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="loading" @click="importHolidays">
          {{ loading ? t('common.loading') : t('events.importHolidaysSubmit') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
