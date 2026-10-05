/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'

/**
 * Where a member goes once an examiner closed their station: the next open section of the sheet and
 * who examines it there, so the examiner can send them on before calling the next one.
 */
defineProps<{
  memberName: string
  station: string
  examinerNames: string[]
}>()

const show = defineModel<boolean>('show', {required: true})

const {t} = useI18n()
</script>

<template>
  <Modal v-model="show" size="sm">
    <div class="space-y-4">
      <SubHeader>{{ t('protocol.nextStation.title', {name: memberName}) }}</SubHeader>
      <p class="text-lg font-semibold">{{ station }}</p>
      <p v-if="examinerNames.length > 0">
        {{ t('protocol.nextStation.examiners', {names: examinerNames.join(', ')}) }}
      </p>
      <MutedText v-else tag="p" size="sm">{{ t('protocol.nextStation.anyExaminer') }}</MutedText>
      <PrimaryButton class="w-full" @click="show = false">{{ t('protocol.nextStation.done') }}</PrimaryButton>
    </div>
  </Modal>
</template>
