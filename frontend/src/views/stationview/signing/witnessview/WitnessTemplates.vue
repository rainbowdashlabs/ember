/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {WitnessTemplateResponse} from '@/api/generated/schema'

/**
 * The templates a document can be made from on the spot for the person, every one that asks the member or
 * a guardian to sign. Making one files it with the person alone.
 */
defineProps<{
  templates: WitnessTemplateResponse[]
  busy: boolean
}>()

defineEmits<{generate: [templateId: number]}>()

const {t} = useI18n()
</script>

<template>
  <section class="space-y-2" data-testid="witness-templates">
    <SubHeader>{{ t('signing.witness.document.templates') }}</SubHeader>
    <MutedText v-if="templates.length === 0" tag="p" size="sm">{{ t('signing.witness.document.noTemplates') }}</MutedText>
    <ul class="space-y-2">
      <li v-for="template in templates" :key="template.templateId">
        <SecondaryButton type="button" class="w-full justify-start" :icon="['fas', 'file-circle-plus']" :disabled="busy"
                         :data-testid="`witness-template-${template.templateId}`" @click="$emit('generate', template.templateId)">
          {{ template.name }}
        </SecondaryButton>
      </li>
    </ul>
  </section>
</template>
