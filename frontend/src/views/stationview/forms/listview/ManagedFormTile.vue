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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {FormStatus} from '@/api/types'
import type {Form} from '@/api/types'
import {formatDate} from '@/util/format'

const props = defineProps<{
  form: Form
  canCreatePolls: boolean
  statusLabel: (status: string) => string
}>()

const emit = defineEmits<{
  (e: 'open', form: Form): void
  (e: 'publish', form: Form): void
  (e: 'close', form: Form): void
  (e: 'edit', form: Form): void
  (e: 'analytics', form: Form): void
  (e: 'delete', form: Form): void
}>()

const {t} = useI18n()
const menuOpen = ref(false)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function pick(action: 'publish' | 'close' | 'edit' | 'analytics' | 'delete') {
  menuOpen.value = false
  emit(action, props.form)
}

function onClickOutside(event: MouseEvent) {
  if (!menuOpen.value) return
  const target = event.target as HTMLElement
  if (!target.closest(`[data-form-menu="${props.form.id}"]`)) menuOpen.value = false
}

document.addEventListener('click', onClickOutside)
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <NeutralContainer class="relative cursor-pointer hover:border-primary transition-colors h-full" @click="emit('open', form)">
    <div class="flex flex-col gap-2 pr-10 h-full">
      <div class="flex items-center gap-2 flex-wrap">
        <SuccessBadge v-if="form.status === FormStatus.OPEN">{{ statusLabel(form.status) }}</SuccessBadge>
        <ErrorBadge v-else-if="form.status === FormStatus.CLOSED">{{ statusLabel(form.status) }}</ErrorBadge>
        <InfoBadge v-else>{{ statusLabel(form.status) }}</InfoBadge>
        <SecondaryBadge v-if="form.status !== FormStatus.DRAFT">
          {{ t('forms.responseCount', {count: form.responseCount}) }}
        </SecondaryBadge>
        <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']"/>
      </div>
      <div class="font-semibold">{{ form.title }}</div>
      <p v-if="form.description" class="text-xs text-(--text-muted) line-clamp-3">{{ form.description }}</p>
      <div class="mt-auto pt-2 text-xs text-(--text-muted) flex flex-col gap-0.5">
        <span>{{ t('forms.createdOn', {when: formatDate(form.createdAt)}) }}</span>
        <span>{{ t('forms.lastActivityOn', {when: formatDate(form.lastActivityAt ?? form.updatedAt)}) }}</span>
      </div>
    </div>

    <div class="absolute top-2 right-2" :data-form-menu="form.id" @click.stop>
      <IconButton :icon="['fas', 'ellipsis-vertical']" :label="t('forms.actions')" @click="toggleMenu"/>
      <div v-if="menuOpen" class="absolute right-0 top-full mt-1 w-48 rounded-theme border border-(--border) bg-(--bg) shadow-lg z-20">
        <DropdownMenuItem v-if="canCreatePolls && form.status === FormStatus.DRAFT" :icon="['fas', 'paper-plane']" @click="pick('publish')">
          {{ t('forms.publish') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="canCreatePolls && form.status === FormStatus.OPEN" :icon="['fas', 'lock']" @click="pick('close')">
          {{ t('forms.close') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="canCreatePolls && form.status !== FormStatus.CLOSED" :icon="['fas', 'pen']" @click="pick('edit')">
          {{ t('forms.edit') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="form.status !== FormStatus.DRAFT" :icon="['fas', 'chart-bar']" @click="pick('analytics')">
          {{ t('forms.viewAnalytics') }}
        </DropdownMenuItem>
        <DropdownMenuItem v-if="canCreatePolls" :icon="['fas', 'trash']" icon-class="w-4 text-(--error)" @click="pick('delete')">
          {{ t('forms.delete') }}
        </DropdownMenuItem>
      </div>
    </div>
  </NeutralContainer>
</template>
