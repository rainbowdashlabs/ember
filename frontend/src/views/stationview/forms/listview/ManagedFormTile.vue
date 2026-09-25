/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onBeforeUnmount, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {RouteLocationRaw} from 'vue-router'
import IconButton from '@/components/button/IconButton.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import {FormPurpose, FormStatus, FormVisibility, type Form} from '@/api/forms'
import {PublicFormState} from '@/api/publicForms'
import {formStateOf} from '@/util/formState'
import {formatDate} from '@/util/format'

const props = defineProps<{
  form: Form
  canCreatePolls: boolean
  /** Names whether a form is taking answers, which is not the same as its stored status. */
  statusLabel: (state: string) => string
  /** The page the tile opens, or nothing where it opens none. */
  to: RouteLocationRaw | null
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
const menuOpen = ref(false)

/** Whether the form is taking answers, which its dates decide as much as its status does. */
const state = computed(() => formStateOf(props.form))

/**
 * A form answered from outside that has been set to answer at its link and nowhere else. Worth
 * saying on the tile, because it is the difference between a form anybody can find and one only the
 * people who were sent it can.
 */
const sentByLinkAlone = computed(
    () => props.form.purpose !== FormPurpose.INTERNAL && props.form.visibility === FormVisibility.UNLISTED)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

const menuActions = {
  publish: () => emit('publish', props.form),
  close: () => emit('close', props.form),
  edit: () => emit('edit', props.form),
  analytics: () => emit('analytics', props.form),
  share: () => emit('share', props.form),
  clear: () => emit('clear', props.form),
  delete: () => emit('delete', props.form),
}

function pick(action: keyof typeof menuActions) {
  menuOpen.value = false
  menuActions[action]()
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
  <RowLink :to="props.to">
    <NeutralContainer class="relative cursor-pointer hover:border-primary transition-colors h-full">
      <div class="flex flex-col gap-2 pr-10 h-full">
        <div class="flex items-center gap-2 flex-wrap">
          <SuccessBadge v-if="state === PublicFormState.OPEN">{{ statusLabel(state) }}</SuccessBadge>
          <ErrorBadge v-else-if="state === PublicFormState.CLOSED">{{ statusLabel(state) }}</ErrorBadge>
          <InfoBadge v-else>{{ statusLabel(state) }}</InfoBadge>
          <SecondaryBadge v-if="form.status !== FormStatus.DRAFT">
            {{ t('forms.responseCount', {count: form.responseCount}) }}
          </SecondaryBadge>
          <InfoBadge v-if="sentByLinkAlone">{{ t('forms.unlistedBadge') }}</InfoBadge>
          <MutedIcon v-if="form.restricted" :icon="['fas', 'lock']"/>
        </div>
        <div class="font-semibold">{{ form.title }}</div>
        <p v-if="form.description" class="text-xs text-(--text-muted) line-clamp-3">{{ form.description }}</p>
        <div class="mt-auto pt-2 text-xs text-(--text-muted) flex flex-col gap-0.5">
          <span>{{ t('forms.createdOn', {when: formatDate(form.createdAt)}) }}</span>
          <span>{{ t('forms.lastActivityOn', {when: formatDate(form.lastActivityAt ?? form.updatedAt)}) }}</span>
        </div>
      </div>

      <div class="absolute top-2 right-2" :data-form-menu="form.id">
        <IconButton :icon="['fas', 'ellipsis-vertical']" :label="t('forms.actions')" @click="toggleMenu"/>
        <div v-if="menuOpen" class="absolute right-0 top-full mt-1 w-48 rounded-theme border border-(--border) bg-(--bg) shadow-lg z-20">
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
    </NeutralContainer>
  </RowLink>
</template>
