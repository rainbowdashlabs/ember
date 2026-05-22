/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'

const { t } = useI18n()

const demoContent = ref(`# Willkommen zum Texteditor

Das ist ein **fetter Text**, das ist *kursiv* und das ist ~~durchgestrichen~~.

Du kannst auch <u>unterstrichenen</u> und ==markierten== Text verwenden.

## Listen

Hier eine Aufzählung:

- Erster Punkt
- Zweiter Punkt
- Dritter Punkt

Und eine nummerierte Liste:

1. Schritt eins
2. Schritt zwei
3. Schritt drei

## Zitate und Code

> Das ist ein Zitat. Hier kann man wichtige Informationen hervorheben.

Inline-Code sieht so aus: \`const x = 42\`

\`\`\`
// Ein Codeblock
function hallo() {
  console.log("Hallo Welt!")
}
\`\`\`

## Tabellen

| Spalte A | Spalte B | Spalte C |
|----------|----------|----------|
| Zeile 1  | Wert 1   | Daten 1  |
| Zeile 2  | Wert 2   | Daten 2  |
| Zeile 3  | Wert 3   | Daten 3  |

## Links

Hier ist ein [Link zur Startseite](/).

---

Das war eine horizontale Trennlinie. Damit kannst du Abschnitte visuell trennen.

## Überschriftenebenen

### Das ist eine H3-Überschrift

Normaler Absatztext darunter.`)
</script>

<template>
  <HelpArticle :title="t('helpCenter.kb.editor.title')" :subtitle="t('helpCenter.kb.editor.subtitle')">
    <HelpSection :title="t('helpCenter.kb.editor.whatIs')">
      <p>{{ t('helpCenter.kb.editor.whatIsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.toolbarTitle')">
      <p>{{ t('helpCenter.kb.editor.toolbarText') }}</p>
    </HelpSection>

    <!-- Toolbar demo icons -->
    <NeutralContainer>
      <div class="space-y-2 text-sm">
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-medium w-28">Formatierung:</span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'bold']" class="w-3 h-3" /> Fett
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'italic']" class="w-3 h-3" /> Kursiv
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'underline']" class="w-3 h-3" /> Unterstrichen
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'strikethrough']" class="w-3 h-3" /> Durchgestrichen
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'code']" class="w-3 h-3" /> Code
          </span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-medium w-28">Struktur:</span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'heading']" class="w-3 h-3" /><span class="text-[10px] font-bold">1</span> <span class="text-[10px] font-bold">2</span> <span class="text-[10px] font-bold">3</span> Überschriften
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'list-ul']" class="w-3 h-3" /> Aufzählung
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'list-ol']" class="w-3 h-3" /> Nummeriert
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'quote-left']" class="w-3 h-3" /> Zitat
          </span>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <span class="font-medium w-28">Medien:</span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'link']" class="w-3 h-3" /> Link
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'table-columns']" class="w-3 h-3" /> Tabelle
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'highlighter']" class="w-3 h-3" /> Hervorhebung
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fab', 'youtube']" class="w-3 h-3" /> YouTube
          </span>
          <span class="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-[var(--bg)] border border-[var(--border)]">
            <font-awesome-icon :icon="['fas', 'image']" class="w-3 h-3" /> Bild
          </span>
        </div>
      </div>
    </NeutralContainer>

    <HelpSection :title="t('helpCenter.kb.editor.formattingTitle')">
      <p>{{ t('helpCenter.kb.editor.formattingText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.headingsTitle')">
      <p>{{ t('helpCenter.kb.editor.headingsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.listsTitle')">
      <p>{{ t('helpCenter.kb.editor.listsText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.linksTitle')">
      <p>{{ t('helpCenter.kb.editor.linksText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.tableTitle')">
      <p>{{ t('helpCenter.kb.editor.tableText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.bubbleTitle')">
      <p>{{ t('helpCenter.kb.editor.bubbleText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.kb.editor.rawTitle')">
      <p>{{ t('helpCenter.kb.editor.rawText') }}</p>
    </HelpSection>

    <!-- Live demo editor -->
    <HelpSection :title="t('helpCenter.kb.editor.demoTitle')">
      <p class="mb-3 text-sm text-[var(--text-muted)]">Probiere den Editor direkt hier aus — du kannst den Text bearbeiten, formatieren und die Werkzeugleiste testen.</p>
    </HelpSection>

    <MarkdownEditor v-model="demoContent" placeholder="Schreibe etwas..." />

    <HelpTip>{{ t('helpCenter.kb.editor.tip') }}</HelpTip>
  </HelpArticle>
</template>
