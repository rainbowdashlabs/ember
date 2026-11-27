/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ProseContent from '@/components/display/ProseContent.vue'
import {renderMarkdown} from '@/util/markdown'

/**
 * What partner stations read about an inventory, a kind or a piece: plain Markdown with a live
 * preview of how the lending shop shows it. Pictures from the media library are left out on purpose,
 * because a partner on another server cannot load them.
 */
const model = defineModel<string>({required: true})

defineProps<{
  hint?: string
}>()

const {t} = useI18n()
const fieldId = useId()
const hasText = computed(() => model.value.trim().length > 0)
</script>

<template>
  <div class="space-y-1">
    <FieldLabel :for="fieldId">{{ t('inventory.description.label') }}</FieldLabel>
    <TextAreaInput
        :id="fieldId"
        v-model="model"
        :rows="5"
        :placeholder="t('inventory.description.placeholder')"
        data-testid="partner-description"
    />
    <p class="text-xs text-(--text-muted)">{{ hint ?? t('inventory.description.hint') }}</p>
    <div v-if="hasText" class="rounded-lg border border-(--border) p-3" data-testid="partner-description-preview">
      <p class="text-xs font-medium text-(--text-muted) mb-1">{{ t('inventory.description.preview') }}</p>
      <ProseContent v-html="renderMarkdown(model)"/>
    </div>
  </div>
</template>
