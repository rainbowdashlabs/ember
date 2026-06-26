/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MetadataField from './MetadataField.vue'
import type {StationPage} from '@/api/pageManage'

const title = defineModel<string>('title', {required: true})
const slug = defineModel<string>('slug', {required: true})
const parentId = defineModel<number | null>('parentId', {required: true})
const metaDescription = defineModel<string>('metaDescription', {required: true})

const props = defineProps<{
  parentOptions: StationPage[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ t('stationPages.editor.metadata') }}</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-3">
      <MetadataField :label="t('stationPages.editor.titleLabel')">
        <TextInput v-model="title" :placeholder="t('stationPages.editor.titlePlaceholder')"/>
      </MetadataField>
      <MetadataField :label="t('stationPages.editor.slugLabel')">
        <TextInput v-model="slug" :placeholder="t('stationPages.editor.slugPlaceholder')"/>
      </MetadataField>
      <MetadataField :label="t('stationPages.editor.parent')">
        <SelectInput v-model="parentId as unknown as string">
          <option :value="null">{{ t('stationPages.editor.noParent') }}</option>
          <option v-for="p in props.parentOptions" :key="p.id" :value="p.id">{{ p.title }}</option>
        </SelectInput>
      </MetadataField>
      <MetadataField :label="t('stationPages.editor.metaDescription')">
        <TextInput v-model="metaDescription" :placeholder="t('stationPages.editor.metaDescriptionPlaceholder')"/>
      </MetadataField>
    </div>
  </NeutralContainer>
</template>
