/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import type { FormListEntry } from '@/api/forms'

defineProps<{
  form: FormListEntry
  responsesLabel: string
  fillLabel: string
  editResponseLabel: string
}>()

const emit = defineEmits<{
  (e: 'fill', form: FormListEntry): void
}>()
</script>

<template>
  <NeutralContainer>
    <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
      <div class="space-y-1">
        <span class="font-medium">{{ form.title }}</span>
        <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']" class="ml-1"/>
        <p v-if="form.description" class="text-xs text-(--text-muted)">{{ form.description }}</p>
        <p class="text-xs text-(--text-muted)">{{ form.responseCount }} {{ responsesLabel }}</p>
      </div>
      <div>
        <PrimaryButton v-if="!form.hasResponded" @click="emit('fill', form)">
          {{ fillLabel }}
        </PrimaryButton>
        <SecondaryButton v-else @click="emit('fill', form)">
          {{ editResponseLabel }}
        </SecondaryButton>
      </div>
    </div>
  </NeutralContainer>
</template>
