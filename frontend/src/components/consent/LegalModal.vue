/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const props = defineProps<{
  title: string
  loading: boolean
  html: string
}>()

const open = defineModel<boolean>({required: true})

const {t} = useI18n()
</script>

<template>
  <Modal v-model="open">
    <div class="space-y-4 p-4">
      <SubHeader>{{ props.title }}</SubHeader>
      <Spinner v-if="props.loading" size="sm"/>
      <div v-else-if="props.html" class="legal-content max-h-[70vh] overflow-y-auto" v-html="props.html"/>
      <div class="flex justify-end">
        <SecondaryButton @click="open = false">{{ t('common.close') }}</SecondaryButton>
      </div>
    </div>
  </Modal>
</template>
