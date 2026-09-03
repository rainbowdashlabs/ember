/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'

const {t} = useI18n()

const model = defineModel<boolean>({default: false})

const props = withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
  mobileFull?: boolean
  /**
   * Renders above every other modal. Teleported dialogs stack by mount order, and a dialog
   * mounted globally at app start (the step-up prompt) would otherwise sit under whatever
   * feature dialog opened it and never receive a click.
   */
  topmost?: boolean
}>(), {
  size: 'md',
  mobileFull: false,
  topmost: false,
})

const sizeClass = computed(() => {
  switch (props.size) {
    case 'sm': return 'max-w-md'
    case 'lg': return 'max-w-2xl'
    case 'xl': return 'max-w-5xl'
    case '2xl': return 'max-w-7xl'
    case 'full': return 'max-w-[95vw]'
    case 'md':
    default: return 'max-w-lg'
  }
})

const dialog = ref<HTMLElement | null>(null)

/**
 * The button this dialog is answered with.
 *
 * <p>Named outright where a dialog marks it, and otherwise the last button in it that is not a way
 * out. Every footer here reads the same way round, cancel and then the thing being confirmed, so
 * the last one is it. Guessing rather than requiring the mark is what makes the rule true in every
 * dialog on the first day instead of in the ones somebody remembered to go back to.
 */
function confirmButton(): HTMLElement | null {
  const root = dialog.value
  if (!root) return null
  const named = root.querySelector<HTMLElement>('[data-confirm]')
  if (named) return named
  const answers = [...root.querySelectorAll<HTMLButtonElement>('button')]
      .filter(button => !button.disabled && !button.hasAttribute('data-cancel'))
  return answers.at(-1) ?? null
}

watch(model, async (open) => {
  if (!open) return
  await nextTick()
  const target = confirmButton() ?? dialog.value
  target?.focus()
})

/**
 * Shift and enter answer the dialog from anywhere inside it, including from a text field, where
 * the enter key belongs to the field itself.
 */
function onKeydown(e: KeyboardEvent) {
  if (e.key !== 'Enter' || !e.shiftKey) return
  const target = confirmButton()
  if (!target) return
  e.preventDefault()
  target.click()
}
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div
          v-if="model"
          :class="['fixed inset-0 flex items-center justify-center', props.topmost ? 'z-[60]' : 'z-50']"
      >
        <!-- Backdrop -->
        <div
            class="absolute inset-0 bg-black/50"
            @click="model = false"
        />
        <!-- Content -->
        <div
            ref="dialog"
            data-testid="modal"
            role="dialog"
            aria-modal="true"
            tabindex="-1"
            @keydown="onKeydown"
            :class="[
              'relative z-10 w-full mx-4 rounded-theme border border-bg-light-accent bg-bg-light p-6 shadow-xl dark:border-bg-dark-accent dark:bg-bg-dark',
              sizeClass,
              props.mobileFull ? 'max-sm:h-full max-sm:mx-0 max-sm:rounded-none max-sm:border-0 max-sm:overflow-y-auto max-sm:flex max-sm:flex-col' : '',
            ]">
          <IconButton
              :icon="['fas', 'xmark']"
              :label="t('common.close')"
              class="absolute top-3 right-3 text-[var(--text-muted)] hover:text-[var(--text)]"
              data-cancel
              @click="model = false"
          >
            <font-awesome-icon :icon="['fas', 'xmark']" class="h-5 w-5"/>
          </IconButton>
          <slot/>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.modal-enter-active,
.modal-leave-active {
  transition: opacity 0.2s ease;
}

.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}
</style>
