/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import {getFindable, setFindable} from '@/api/session'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Whether member managers of every station of this instance can find the reader by name and invite them.
 * Off until the reader turns it on; people who share a station or an association with them find them
 * either way, which the hint says so the switch is not read as hiding them from everybody.
 */
const {t} = useI18n()
const labelId = useId()
const findable = ref(false)

const {loading, failure: loadFailure} = useAsyncLoader(async () => {
  findable.value = await getFindable()
})

const {running, failure, run: choose} = useAsyncAction(async (wanted: boolean) => {
  await setFindable(wanted)
  findable.value = wanted
})
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="findable-switch">
    <div class="flex items-center justify-between gap-4">
      <div>
        <SubHeader :id="labelId">{{ t('accountLinks.findableTitle') }}</SubHeader>
        <MutedText tag="p" size="sm">{{ t('accountLinks.findableHint') }}</MutedText>
      </div>
      <ToggleInput
          :model-value="findable"
          :disabled="loading || running || loadFailure !== null"
          :aria-labelledby="labelId"
          @update:model-value="choose"
      />
    </div>
    <FailureAlert :failure="loadFailure ?? failure"/>
  </NeutralContainer>
</template>
