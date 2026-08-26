/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import {movements} from '@/api'
import {apiErrorMessage} from '@/util/apiError'

/**
 * Asks a member for everything they hold, each piece on the chain that fits it.
 *
 * <p>One chain per piece, because the pieces go different ways: what the station owns goes back on
 * its shelf and what the body above it owns goes into the post. A single movement for the lot would
 * have to end in two places at once.
 */
const props = defineProps<{
  memberId: number
}>()

const emit = defineEmits<{done: []}>()

const {t} = useI18n()

const busy = ref(false)
const error = ref('')
const success = ref('')

async function returnEverything() {
  busy.value = true
  error.value = ''
  success.value = ''
  try {
    const started = await movements.returnEverything(props.memberId)
    success.value = t('inventory.member.returnEverythingDone', {count: started.length})
    emit('done')
  } catch (e) {
    error.value = apiErrorMessage(e) ?? t('common.error')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="space-y-2">
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Alert v-if="success" variant="success">{{ success }}</Alert>
    <SecondaryButton :disabled="busy" data-testid="return-everything" @click="returnEverything">
      {{ t('inventory.member.returnEverything') }}
    </SecondaryButton>
  </div>
</template>
