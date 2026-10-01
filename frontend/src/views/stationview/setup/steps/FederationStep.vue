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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import DiscoveryVisibilityChoice from '@/views/stationview/setup/steps/federationstep/DiscoveryVisibilityChoice.vue'
import PublicListingContents from '@/views/stationview/setup/steps/federationstep/PublicListingContents.vue'
import {stationManage} from '@/api'
import {DiscoveryVisibility, type DiscoveryVisibilityName} from '@/api/stationManage'
import {useSetupStatus} from '@/composables/useSetupStatus'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {goToNextStep} from '@/views/stationview/setup/steps'

/**
 * The step where a station decides how it is listed in discovery, knowing what a listing shows and
 * where it goes. It opens on the station's current setting, which for a new station is a public
 * listing, and saving it unchanged is a decision like any other and completes the step.
 */
const {t} = useI18n()
const router = useRouter()
const {reload} = useSetupStatus()

const stationName = ref('')
const visibility = ref<DiscoveryVisibilityName>(DiscoveryVisibility.PUBLIC)
const description = ref('')
const showKb = ref(true)
const loading = ref(true)

onMounted(async () => {
    try {
        const info = await stationManage.getStationInfo()
        stationName.value = info.name
        description.value = info.discoveryDescription ?? ''
        showKb.value = info.discoveryShowKb
        visibility.value = info.discoveryVisibility
    } catch { /* ignore */ }
    loading.value = false
})

const {running: saving, failure, run: save} = useAsyncAction(async () => {
    await stationManage.updateStationName({
        name: stationName.value,
        discoveryVisibility: visibility.value,
        discoveryDescription: description.value,
        discoveryShowKb: showKb.value,
    })
    await reload()
    goToNextStep(router, 'federation')
})
</script>

<template>
  <SetupLayout step-id="federation" skippable :saving="saving" @save="save">
    <FailureAlert :failure="failure"/>
    <div v-if="!loading" class="space-y-4">
      <DiscoveryVisibilityChoice v-model="visibility"/>
      <PublicListingContents/>
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
