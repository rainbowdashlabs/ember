/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onBeforeUnmount, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import {FormPurpose, FormStatus, type Form} from '@/api/forms'

/**
 * Everything that can be done to a form, on the corner of its tile.
 *
 * <p>Which entries appear follows the form's stored status rather than whether it is taking answers
 * right now: closing a form whose end date has passed is still a thing somebody may want to do, and
 * reopening one is how a poll is asked a second time.
 */
const props = defineProps<{
  form: Form
  canCreatePolls: boolean
}>()

const emit = defineEmits<{
  (e: 'publish', form: Form): void
  (e: 'close', form: Form): void
  (e: 'edit', form: Form): void
  (e: 'analytics', form: Form): void
  (e: 'share', form: Form): void
  (e: 'clear', form: Form): void
  (e: 'delete', form: Form): void
}>()

const {t} = useI18n()
const open = ref(false)

const actions = {
  publish: () => emit('publish', props.form),
  close: () => emit('close', props.form),
  edit: () => emit('edit', props.form),
  analytics: () => emit('analytics', props.form),
  share: () => emit('share', props.form),
  clear: () => emit('clear', props.form),
  delete: () => emit('delete', props.form),
}

function pick(action: keyof typeof actions) {
  open.value = false
  actions[action]()
}

function onClickOutside(event: MouseEvent) {
  if (!open.value) return
  const target = event.target as HTMLElement
  if (!target.closest(`[data-form-menu="${props.form.id}"]`)) open.value = false
}

document.addEventListener('click', onClickOutside)
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div class="absolute top-2 right-2" :data-form-menu="form.id">
    <IconButton :icon="['fas', 'ellipsis-vertical']" :label="t('forms.actions')" @click="open = !open"/>
    <div v-if="open" class="absolute right-0 top-full mt-1 w-48 rounded-theme border border-(--border) bg-(--bg) shadow-lg z-20">
      <DropdownMenuItem v-if="canCreatePolls && form.status === FormStatus.DRAFT" :icon="['fas', 'paper-plane']" @click="pick('publish')">
        {{ t('forms.publish') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="canCreatePolls && form.status === FormStatus.OPEN" :icon="['fas', 'lock']" @click="pick('close')">
        {{ t('forms.close') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="canCreatePolls && form.status === FormStatus.CLOSED" :icon="['fas', 'lock-open']" @click="pick('publish')">
        {{ t('forms.reopen') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="canCreatePolls && form.status !== FormStatus.CLOSED" :icon="['fas', 'pen']" @click="pick('edit')">
        {{ t('forms.edit') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="canCreatePolls && form.purpose !== FormPurpose.INTERNAL" :icon="['fas', 'link']" @click="pick('share')">
        {{ t('forms.share') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="form.status !== FormStatus.DRAFT" :icon="['fas', 'chart-bar']" @click="pick('analytics')">
        {{ t('forms.viewAnalytics') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="canCreatePolls && form.responseCount > 0" :icon="['fas', 'rotate-left']" @click="pick('clear')">
        {{ t('forms.clearResponses') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="canCreatePolls" :icon="['fas', 'trash']" icon-class="w-4 text-(--error)" @click="pick('delete')">
        {{ t('forms.delete') }}
      </DropdownMenuItem>
    </div>
  </div>
</template>
