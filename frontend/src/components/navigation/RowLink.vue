/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import {pressedAControl} from '@/util/rowPress'

/**
 * A row, a card or a name that opens a page, said as the link it is.
 *
 * <p>Pressing one of these has always opened a page, but written as a click handler it was not a
 * link: no middle click, no address to copy, nothing in the status bar, nothing in the tab order and
 * nothing for a screen reader to announce or to list. None of that is visible to somebody testing
 * with a mouse, which is how it spread.
 *
 * <p>This wraps the row and nothing else. The card keeps its own border, padding and hover, so a
 * reader cannot tell which release this landed in except by trying to middle-click. Keeping the
 * look takes `row-link` as well as the utilities beside it: the rule painting links lives outside
 * Tailwind's layers, where it beats any utility whatever the specificity, so `style.css` names this
 * class as its exception. Without it a row was left alone only where it happened to hold a button,
 * which is what the rule tests for, and painted like a link everywhere else.
 *
 * <p>Only for a page the reader chose to open. Going on after doing something, and going back, are
 * not links: the first opens a page that did not exist until the act, and the second restores a
 * place rather than addressing one.
 */
const props = defineProps<{
  /**
   * Where the row leads, or nothing where the reader may not open it. Such a row renders as it
   * always did rather than as a link to a refusal.
   */
  to?: RouteLocationRaw | null
}>()

/**
 * Lets a control inside the row do its own thing without opening the row as well.
 *
 * <p>`@click.stop` on the button is not enough and never was, which is the trap this component
 * exists to close: stopping the event travelling upward does nothing about the browser following
 * the link it happened inside, so a delete button in a row would delete and then navigate. The row
 * asks once what was actually pressed, so no button has to remember.
 *
 * <p>It listens while the press travels down rather than up, and that is not a detail. The link's
 * own handler sits on the same element, and a listener passed in from outside is added after the
 * one the link rendered with, so on the way up this would run second: the route would already have
 * been pushed by the time anything refused it. Caught on the way down it runs first, and the link
 * finds the press already answered.
 */
function leaveControlsAlone(event: MouseEvent) {
  if (pressedAControl(event)) {
    event.preventDefault()
  }
}
</script>

<template>
  <NuxtLink
      v-if="props.to"
      :to="props.to"
      class="row-link block text-inherit no-underline hover:no-underline"
      @click.capture="leaveControlsAlone"
  >
    <slot/>
  </NuxtLink>
  <slot v-else/>
</template>
