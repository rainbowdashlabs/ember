/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SigningStepFrame from '../signingview/SigningStepFrame.vue'

/**
 * The screen the witness hands the device over on: it says plainly whom it is for, and the person takes it
 * up by saying who they are.
 */
const props = defineProps<{
  name: string
  position: number
  total: number
}>()

defineEmits<{next: []}>()

const {t} = useI18n()

const title = computed(() => t('signing.witness.handOver.heading', {name: props.name}))
</script>

<template>
  <SigningStepFrame :title="title" :position="position" :total="total">
    <p class="text-sm">{{ t('signing.witness.handOver.hint', {name}) }}</p>
    <template #actions>
      <PrimaryButton type="button" :icon="['fas', 'hand-holding']" data-testid="witness-handed-over" @click="$emit('next')">
        {{ t('signing.witness.handOver.ready', {name}) }}
      </PrimaryButton>
    </template>
  </SigningStepFrame>
</template>
