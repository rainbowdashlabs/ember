/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import MarkdownEditor from '@/components/input/MarkdownEditor.vue'
import DummyToolbar from '@/views/helpcenter/stationview/knowledge/editorhelp/DummyToolbar.vue'

const {t} = useI18n()

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
function hallo() {
  return "Hallo Welt!"
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
      <DummyToolbar class="mt-3" />
    </HelpSection>

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

    <HelpSection :title="t('helpCenter.kb.editor.demoTitle')">
      <p class="mb-3 text-sm text-[var(--text-muted)]">Probiere den Editor direkt hier aus - du kannst den Text bearbeiten, formatieren und die Werkzeugleiste testen.</p>
      <MarkdownEditor v-model="demoContent" placeholder="Schreibe etwas..." />
    </HelpSection>

    <HelpTip>{{ t('helpCenter.kb.editor.tip') }}</HelpTip>
  </HelpArticle>
</template>
