/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import TextInput from '@/components/input/text/TextInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

const sizes = defineModel<string[]>('sizes', {required: true})

const props = defineProps<{
  saving: boolean
}>()

const emit = defineEmits<{
  back: []
  submit: []
}>()

const {t} = useI18n()

const newSizeLabel = ref('')

function addSize() {
  if (!newSizeLabel.value.trim()) return
  sizes.value = [...sizes.value, newSizeLabel.value.trim()]
  newSizeLabel.value = ''
}

function removeSize(index: number) {
  sizes.value = sizes.value.filter((_, i) => i !== index)
}
</script>

<template>
  <p class="text-sm text-(--text-muted)">{{ t('inventory.manage.sizesHint') }}</p>

  <div class="flex items-center gap-2">
    <TextInput v-model="newSizeLabel" :placeholder="t('inventory.manage.sizeLabel')" class="flex-1" @keyup.enter="addSize" />
    <SecondaryButton :disabled="!newSizeLabel.trim()" @click="addSize">
      <font-awesome-icon :icon="['fas', 'plus']" />
    </SecondaryButton>
  </div>

  <div v-if="sizes.length > 0" class="space-y-1">
    <div v-for="(size, idx) in sizes" :key="idx" class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
      <span class="text-sm">{{ size }}</span>
      <MutedIconButton :icon="['fas', 'xmark']" :label="t('common.delete')" hover="error" class="text-sm" @click="removeSize(idx)"/>
    </div>
  </div>

  <div class="flex justify-between gap-3">
    <SecondaryButton @click="emit('back')">{{ t('inventory.manage.back') }}</SecondaryButton>
    <PrimaryButton :disabled="props.saving || sizes.length === 0" @click="emit('submit')">
      {{ props.saving ? t('common.loading') : t('common.save') }}
    </PrimaryButton>
  </div>
</template>
