/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {ClusterKbFolder} from '@/api/clusterContent'

defineProps<{
  folders: readonly ClusterKbFolder[]
  busy: boolean
}>()

const emit = defineEmits<{
  publish: [name: string, content: string, folderId: number | null]
}>()

const {t} = useI18n()

const name = ref('')
const body = ref('')
const folder = ref<string>('')

function publish() {
  if (!name.value.trim()) return
  emit('publish', name.value.trim(), body.value, folder.value ? Number(folder.value) : null)
  name.value = ''
  body.value = ''
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('clusterKnowledge.articleTitle') }}</SectionHeader>

    <div class="space-y-1">
      <FormLabel>{{ t('clusterKnowledge.nameLabel') }}</FormLabel>
      <TextInput v-model="name" :placeholder="t('clusterKnowledge.namePlaceholder')"/>
    </div>

    <div class="space-y-1">
      <FormLabel>{{ t('clusterKnowledge.folderLabel') }}</FormLabel>
      <SelectInput v-model="folder">
        <option value="">{{ t('clusterKnowledge.noFolder') }}</option>
        <option v-for="entry in folders" :key="entry.id" :value="String(entry.id)">{{ entry.name }}</option>
      </SelectInput>
    </div>

    <div class="space-y-1">
      <FormLabel>{{ t('clusterKnowledge.bodyLabel') }}</FormLabel>
      <TextAreaInput v-model="body" :rows="6"/>
    </div>

    <PrimaryButton :disabled="busy || !name.trim()" @click="publish">
      {{ t('clusterKnowledge.publish') }}
    </PrimaryButton>
  </NeutralContainer>
</template>
