/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter, type RouteLocationRaw} from 'vue-router'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {goBack} from '@/util/goBack'

/**
 * The head of an appointment's page and of an event group's page alike: what it is on the left, and on
 * the right Back, the primary Edit and one actions menu with everything done only now and then. Deleting
 * comes last in the menu, coloured, and asks before it deletes unless shift is held.
 *
 * <p>Back shows only its arrow on a phone, so the three controls share one line there instead of
 * stacking full width above the tabs.
 */
const props = defineProps<{
  /** Where Back leads when the page was opened directly rather than from another page. */
  backTo: RouteLocationRaw
  /** The edit page, or nothing where the reader may not edit. */
  editTo?: RouteLocationRaw | null
  editLabel: string
  editTestId?: string
  /** Identifies this page's menu; the trigger carries it with `-trigger`. */
  menuTestId: string
  /** Whether the default slot holds menu entries for this reader. */
  hasMenuItems?: boolean
  /** Deletes what the page shows and leaves it; absent where the reader may not delete. */
  remove?: (() => Promise<unknown>) | null
  deleteLabel?: string
  deleteTitle?: string
  deleteMessage?: string
  deleteTestId?: string
}>()

const {t} = useI18n()
const router = useRouter()

const hasMenu = computed(() => props.hasMenuItems || !!props.remove)

const {running: deleting, failure: deleteFailure, run: runDelete} = useAsyncAction(async () => {
  await props.remove?.()
})

const {show: confirmOpen, request: requestDelete, confirm: confirmDelete} = useConfirmAction<true>({
  onConfirm: async () => { await runDelete() },
})
</script>

<template>
  <div class="space-y-3">
    <FailureAlert v-if="deleteFailure" :failure="deleteFailure"/>
    <div class="flex items-center justify-between flex-wrap gap-3">
      <div class="flex items-center gap-3 flex-wrap min-w-0">
        <slot name="title"/>
      </div>
      <div class="flex items-center gap-2 ml-auto" data-testid="detail-page-actions">
        <SecondaryButton :icon="['fas', 'arrow-left']" data-testid="detail-page-back" @click="goBack(router, props.backTo)">
          <span class="max-sm:sr-only">{{ t('common.back') }}</span>
        </SecondaryButton>
        <PrimaryButton v-if="props.editTo" :icon="['fas', 'pen']" :to="props.editTo" :data-testid="props.editTestId">
          {{ props.editLabel }}
        </PrimaryButton>
        <ActionsMenu v-if="hasMenu" :label="t('common.actions')" :test-id="props.menuTestId">
          <slot/>
          <DropdownMenuItem v-if="props.remove" :icon="['fas', 'trash']" destructive :data-testid="props.deleteTestId"
                            @click="requestDelete(true)">
            {{ props.deleteLabel ?? t('common.delete') }}
          </DropdownMenuItem>
        </ActionsMenu>
      </div>
    </div>
    <ConfirmDeleteModal
        v-model="confirmOpen"
        :title="props.deleteTitle"
        :message="props.deleteMessage ?? ''"
        :busy="deleting"
        @confirm="confirmDelete"
    />
  </div>
</template>
