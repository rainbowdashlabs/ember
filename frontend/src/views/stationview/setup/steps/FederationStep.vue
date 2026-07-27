/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SetupLayout from '@/views/stationview/setup/SetupLayout.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {stationManage} from '@/api'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {nextStep, stepRouteName} from '@/views/stationview/setup/steps'

const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

const stationName = ref('')
const visibility = ref('PUBLIC')
const description = ref('')
const showKb = ref(true)
const loading = ref(true)

onMounted(async () => {
    try {
        const info = await stationManage.getStationInfo()
        stationName.value = info.name ?? ''
        description.value = info.discoveryDescription ?? ''
        showKb.value = info.discoveryShowKb ?? true
        const adminHasTouched = !!info.discoveryDescription
        visibility.value = adminHasTouched ? (info.discoveryVisibility ?? 'PUBLIC') : 'PUBLIC'
    } catch { /* ignore */ }
    loading.value = false
})

const {running: saving, error, run: save} = useAsyncAction(async () => {
    await stationManage.updateStationName({
        name: stationName.value,
        discoveryVisibility: visibility.value,
        discoveryDescription: description.value,
        discoveryShowKb: showKb.value,
    })
    await reload()
    const next = nextStep('federation')
    if (next) router.push({name: stepRouteName(next)})
})
</script>

<template>
  <SetupLayout step-id="federation" skippable :saving="saving" @save="save">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <p class="text-sm text-(--text-muted)">{{ t('setup.steps.federation.consequence') }}</p>
    <div v-if="!loading" class="space-y-3">
      <label class="block text-sm">
        {{ t('setup.steps.federation.visibility') }}
        <SelectInput v-model="visibility">
          <option value="PUBLIC">{{ t('setup.steps.federation.visibilityPublic') }}</option>
          <option value="NONE">{{ t('setup.steps.federation.visibilityNone') }}</option>
        </SelectInput>
      </label>
      <label class="block text-sm">
        {{ t('setup.steps.federation.description') }}
        <TextAreaInput v-model="description"/>
      </label>
      <label class="flex items-center gap-2 text-sm">
        <ToggleInput v-model="showKb"/>
        {{ t('setup.steps.federation.showKb') }}
      </label>
    </div>
  </SetupLayout>
</template>
