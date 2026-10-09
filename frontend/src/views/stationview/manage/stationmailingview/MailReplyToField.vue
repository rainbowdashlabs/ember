/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import LabelledField from '@/components/input/LabelledField.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {getStationReplyTo, saveStationReplyTo} from '@/api/instanceMail'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Where replies to this station's mail go, whichever provider carries it. Left empty, a reply goes
 * to the address the mail came from.
 */
const {t} = useI18n()

const replyTo = ref('')
const saveFailure = ref<Failure | null>(null)

const {failure} = useAsyncLoader(async (isCurrent) => {
  const loaded = await getStationReplyTo()
  if (isCurrent()) replyTo.value = loaded.replyTo ?? ''
})

async function save() {
  saveFailure.value = null
  try {
    replyTo.value = (await saveStationReplyTo(replyTo.value.trim())).replyTo ?? ''
  } catch (e) {
    saveFailure.value = describeFailure(e, t)
    throw e
  }
}
</script>

<template>
  <div class="space-y-2">
    <FailureAlert :failure="failure ?? saveFailure"/>
    <LabelledField :label="t('instanceMail.station.replyTo')" :help="t('instanceMail.station.replyToHint')" hint>
      <TextInput v-model="replyTo" :placeholder="t('instanceMail.station.replyToPlaceholder')" autocomplete="email"/>
    </LabelledField>
    <ButtonRow align="end">
      <SaveButton :action="save"/>
    </ButtonRow>
  </div>
</template>
