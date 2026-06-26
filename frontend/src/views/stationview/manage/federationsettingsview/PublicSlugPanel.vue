/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const slug = defineModel<string>('slug', {required: true})

const {t} = useI18n()

const slugUrl = computed(() => {
  if (!slug.value) return ''
  return `${window.location.origin}/public/station/${slug.value}`
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('stationManage.publicSlug.title') }}</SubHeader>
    <MutedText size="sm">{{ t('stationManage.publicSlug.hint') }}</MutedText>
    <div class="space-y-1">
      <FieldLabel>{{ t('stationManage.publicSlug.slug') }}</FieldLabel>
      <TextInput v-model="slug" :placeholder="t('stationManage.publicSlug.placeholder')"/>
      <MutedText v-if="slug" size="sm">{{ slugUrl }}</MutedText>
    </div>
  </NeutralContainer>
</template>
