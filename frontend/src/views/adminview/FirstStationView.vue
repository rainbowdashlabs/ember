/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {foundFirstStation} from '@/api/stations'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useSession} from '@/composables/useSession'
import {useStations} from '@/composables/useStations'

/**
 * Where the administrator of a fresh instance founds its first station.
 *
 * <p>A new installation creates its administrator and no station, so the first station carries a
 * name somebody chose. The administrator names it here, becomes its manager and goes straight on into
 * its setup, where the remaining settings, how it is listed among them, are decided step by step.
 */
const {t} = useI18n()
const router = useRouter()
const {setActiveStation, clear: forgetStations} = useStations()
const {clear: forgetSession} = useSession()

const name = ref('')
const canFound = computed(() => name.value.trim().length > 0)

const {running, failure, run: found} = useAsyncAction(async () => {
  const station = await foundFirstStation(name.value.trim())
  forgetStations()
  forgetSession()
  setActiveStation(station.stationUid)
  await router.push({name: 'station-setup'})
})
</script>

<template>
  <ViewContent :title="t('pages.admin-first-station.title')" :subtitle="t('pages.admin-first-station.subtitle')">
    <div class="max-w-2xl mx-auto space-y-4">
      <p>{{ t('firstStation.intro') }}</p>
      <MutedText tag="p" size="sm">{{ t('firstStation.afterwards') }}</MutedText>
      <FailureAlert :failure="failure"/>
      <NeutralContainer>
        <form class="space-y-3" @submit.prevent="canFound && found()">
          <FieldLabel>
            {{ t('firstStation.nameLabel') }}
            <TextInput v-model="name" :placeholder="t('firstStation.namePlaceholder')" data-testid="first-station-name"/>
          </FieldLabel>
          <MutedText tag="p" size="xs">{{ t('firstStation.nameHint') }}</MutedText>
          <PrimaryButton type="submit" :icon="['fas', 'building']" :disabled="!canFound || running" data-testid="first-station-found">
            {{ t('firstStation.found') }}
          </PrimaryButton>
        </form>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
