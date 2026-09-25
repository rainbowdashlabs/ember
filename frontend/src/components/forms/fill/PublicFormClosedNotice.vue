/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import Alert from '@/components/feedback/Alert.vue'
import {PublicFormState, type PublicFormStateName} from '@/api/publicForms'

/**
 * Why a form is not taking answers, said where the fields would have been.
 *
 * <p>The fields, the consent box and the send button are all absent in this case, so nobody types
 * an answer into something that will refuse it. Before this, every form drew its questions whatever
 * its state and the reader learned it was shut only after pressing send, in a message that told them
 * to try again.
 */
const props = defineProps<{
  state: PublicFormStateName
}>()

const {t} = useI18n()

const message = computed(() => {
  if (props.state === PublicFormState.NOT_PUBLISHED) return t('publicForm.notPublishedYet')
  if (props.state === PublicFormState.NOT_OPEN_YET) return t('publicForm.notOpenYet')
  return t('publicForm.closed')
})
</script>

<template>
    <Alert variant="info">{{ message }}</Alert>
</template>
