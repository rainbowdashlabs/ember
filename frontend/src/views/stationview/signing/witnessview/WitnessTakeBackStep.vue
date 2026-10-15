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
 * The person hands the device back to the witness, signed or not. Only the witness goes on from here.
 */
const props = defineProps<{
  witness: string
  abandoned: boolean
  position: number
  total: number
}>()

defineEmits<{next: []}>()

const {t} = useI18n()

const title = computed(() => t('signing.witness.takeBack.heading', {witness: props.witness}))
</script>

<template>
  <SigningStepFrame :title="title" :position="position" :total="total">
    <p class="text-sm">
      {{ abandoned ? t('signing.witness.takeBack.abandoned') : t('signing.witness.takeBack.hint', {witness}) }}
    </p>
    <template #actions>
      <PrimaryButton type="button" data-testid="witness-taken-back" @click="$emit('next')">
        {{ t('signing.witness.takeBack.taken') }}
      </PrimaryButton>
    </template>
  </SigningStepFrame>
</template>
