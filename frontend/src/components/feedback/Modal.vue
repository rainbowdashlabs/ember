/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, type ComponentPublicInstance} from 'vue'
import {useI18n} from 'vue-i18n'
import {DialogContent, DialogOverlay, DialogPortal, DialogRoot} from 'reka-ui'
import IconButton from '@/components/button/IconButton.vue'
import ModalBody from '@/components/feedback/ModalBody.vue'
import {useDialogOverlay} from '@/components/feedback/dialogLayers'

/**
 * The dialog behind every screen that asks something on top of a page.
 *
 * <p>It is a modal dialog in the full sense: Escape and the close button close it, so does a press
 * on the dimmed page around it, the focus is held inside while it is open and goes back to where
 * it came from once it closes, and the page underneath neither scrolls nor reaches a screen reader.
 * The dialog is labelled by the first heading of its content.
 *
 * <p>Which of two open dialogs is in front is decided by the order they were opened in, through
 * the layers in `dialogLayers`, since the order they were mounted in says nothing about it.
 */
defineOptions({inheritAttrs: false})

const {t} = useI18n()

const model = defineModel<boolean>({default: false})

const props = withDefaults(defineProps<{
  size?: 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full'
  mobileFull?: boolean
  /**
   * Takes the dialog off the screen without closing it, so that what is behind it can be seen or
   * photographed and the half-filled form inside is still there afterwards. Closing it would take
   * the form with it, and unmounting would take whatever it is in the middle of doing.
   */
  hidden?: boolean
}>(), {
  size: 'md',
  mobileFull: false,
  hidden: false,
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

const dialog = ref<ComponentPublicInstance | null>(null)
const {overlay, layer, keepOpenUnlessOverlay} = useDialogOverlay(model)

/**
 * The button this dialog is answered with.
 *
 * <p>Named outright where a dialog marks it, and otherwise the last button in it that is not a way
 * out. Every footer here reads the same way round, cancel and then the thing being confirmed, so
 * the last one is it. Guessing rather than requiring the mark is what makes the rule true in every
 * dialog on the first day instead of in the ones somebody remembered to go back to.
 */
function confirmButton(): HTMLElement | null {
  const root: HTMLElement | undefined = dialog.value?.$el
  if (!root) return null
  const named = root.querySelector<HTMLElement>('[data-confirm]')
  if (named) return named
  const answers = [...root.querySelectorAll<HTMLButtonElement>('button')]
      .filter(button => !button.disabled && !button.hasAttribute('data-cancel'))
  return answers.at(-1) ?? null
}

/** The dialog opens with the focus on the button it is answered with, so enter alone answers it. */
function focusAnswer(event: Event) {
  const target: HTMLElement | null | undefined = confirmButton() ?? dialog.value?.$el
  if (!target) return
  event.preventDefault()
  target.focus()
}

/**
 * Escape closes the dialog from inside a text editor too. The editor marks every escape it sees as
 * handled, and a handled escape is otherwise taken to belong to whatever inside the dialog claimed it,
 * so the dialog would stay open for as long as the cursor is in the text.
 */
function closeFromTextEditor(event: KeyboardEvent) {
  if (!event.defaultPrevented) return
  if (!(event.target instanceof Element) || !event.target.closest('.ProseMirror')) return
  model.value = false
}

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
  <DialogRoot v-model:open="model">
    <DialogPortal>
      <DialogOverlay
          ref="overlay"
          class="fixed inset-0 flex items-center justify-center bg-black/50 data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out"
          :class="{'invisible opacity-0': props.hidden}"
          :style="{zIndex: layer}"
      >
        <DialogContent
            ref="dialog"
            data-testid="modal"
            :aria-describedby="undefined"
            :class="[
              'data-[state=open]:animate-fade-in data-[state=closed]:animate-fade-out relative w-full mx-4 rounded-theme border border-bg-light-accent bg-bg-light p-6 shadow-xl dark:border-bg-dark-accent dark:bg-bg-dark',
              'flex flex-col max-h-[90dvh] outline-none',
              sizeClass,
              props.mobileFull ? 'max-sm:h-full max-sm:mx-0 max-sm:rounded-none max-sm:border-0 max-sm:overflow-y-auto max-sm:flex max-sm:flex-col' : '',
            ]"
            @keydown="onKeydown"
            @escape-key-down="closeFromTextEditor"
            @open-auto-focus="focusAnswer"
            @pointer-down-outside="keepOpenUnlessOverlay"
        >
          <IconButton
              :icon="['fas', 'xmark']"
              :label="t('common.close')"
              class="absolute top-3 right-3 z-10 text-[var(--text-muted)] hover:text-[var(--text)]"
              data-cancel
              @click="model = false"
          />
          <ModalBody>
            <slot/>
          </ModalBody>
        </DialogContent>
      </DialogOverlay>
    </DialogPortal>
  </DialogRoot>
</template>
