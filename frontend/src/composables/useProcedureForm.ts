/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { procedures } from '@/api'
import { StationPermission, type ProcedureItemRequest, type ProcedureStepKind, type ProcedureTemplate, type ProcedureTemplateDetail, type StepDocument } from '@/api/generated/schema'
import type { RestrictionSelection } from '@/api/types'
import { emptyRestriction, namesSomebody, toRestriction } from '@/components/input/restriction'
import { documentChanged, stepDocumentOf, stepKindChange, type AskedDocument } from '@/components/procedures/stepDocument'
import { loadPartyChoices, noPartyChoices, type PartyChoices } from '@/components/procedures/partyChoices'
import { fromCompletion } from '@/components/input/select/memberOption'
import type { StepDependency } from '@/api/procedures'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useSession } from '@/composables/useSession'
import { describeFailure } from '@/util/failure'
import { dateToInstant, instantToDate } from '@/util/format'
import { moveWithin } from '@/util/reorder'

/** A checklist item as the form edits it, known by a temporary id until the procedure is saved. */
export interface EditableItem {
    id?: number
    tempId: number
    title: string
    description: string
    isPublic: boolean
    userAssigned: boolean
    position: number
    dependsOn: number[]
    /** The document the step asks to sign, null for a step ticked by hand. */
    document: AskedDocument | null
    /** Whether the step already made its document, which fixes what it asks for. */
    documentMade: boolean
    /** The document the stored step asks to sign, to send what it asks for only when that changed. */
    storedDocumentId?: number | null
}

/**
 * The editable form behind creating and editing a procedure, including its checklist items and
 * the dependencies between them.
 *
 * Items are identified by a client-side {@code tempId} while the form is open, because an item
 * added here has no server id until the procedure is saved, yet it can already be named as
 * another item's dependency. Saving maps every temp id onto the real one and only then writes
 * the dependency pairs.
 *
 * @param editId           the procedure being edited, or {@code null} when creating a new one
 * @param presetTemplateId a template to start a new procedure from, or {@code null} for a blank one
 */
export function useProcedureForm(editId: Ref<number | null>, presetTemplateId: Ref<number | null>) {
  const { t } = useI18n()
  const { hasPermission } = useSession()

  const name = ref('')
  /**
   * The name the procedure was loaded under, which is what a page titles itself after. The editable
   * field cannot serve: a header rewriting itself letter by letter as somebody renames the procedure
   * is a distraction, and a cleared field would leave the page nameless.
   */
  const savedName = ref('')
  const description = ref('')
  /**
   * The due day as a date field holds it, `yyyy-MM-dd`. The endpoint speaks instants, so it is
   * converted on the way out and back again on the way in rather than being passed straight
   * through, which is a request the backend refuses whole.
   */
  const dueAt = ref('')
  const isPublic = ref(true)
  /** The member the procedure is about, as the picker holds it: the id, or empty for nobody in particular. */
  const about = ref('')
  /** Whom the procedure was loaded about, so saving changes it only where the reader did. */
  const savedAbout = ref('')
  /** Whether the procedure is about somebody already, who can be changed but not taken away. */
  const aboutFixed = computed(() => savedAbout.value !== '')
  /** Whether the procedure has to name somebody it is about, which every new one does. */
  const aboutRequired = computed(() => !isEditMode.value)

  const templates = ref<ProcedureTemplate[]>([])
  const selectedTemplateId = ref<number | null>(null)
  const templateDetail = ref<ProcedureTemplateDetail | null>(null)

  /** Whom the party can name; its members are also whom the procedure can be about. */
  const partyChoices = ref<PartyChoices>(noPartyChoices())
  /** The members the procedure can be about, as the member pickers offer them. */
  const aboutChoices = computed(() => partyChoices.value.members.map(fromCompletion))
  /** The managing party: who sees the public steps, ticks those marked for them and is told. */
  const party = ref<RestrictionSelection>(emptyRestriction())

  const items = ref<EditableItem[]>([])
  const existingItemIds = ref<Set<number>>(new Set())
  let nextTempId = 1

  const isEditMode = computed(() => editId.value != null)

  /**
   * Turns a stored item list plus its dependency pairs into editable items. The pairs reference
   * server ids, so they are translated through the temp id each item is given here.
   */
  function toEditableItems(
    source: {
      id: number
      title: string
      description?: string | null
      isPublic: boolean
      userAssigned: boolean
      position: number
      kind?: ProcedureStepKind
      documentTemplateId?: number | null
      generationId?: number | null
    }[],
    dependencies: number[][],
    documents: readonly StepDocument[],
    keepIds: boolean,
  ): EditableItem[] {
    const realToTemp = new Map<number, number>()
    const editable: EditableItem[] = source.map(item => {
      const tempId = nextTempId++
      realToTemp.set(item.id, tempId)
      const document = stepDocumentOf(item, documents)
      return {
        ...(keepIds ? {id: item.id} : {}),
        tempId,
        title: item.title,
        description: item.description ?? '',
        isPublic: item.isPublic,
        userAssigned: item.userAssigned,
        position: item.position,
        dependsOn: [],
        document,
        documentMade: keepIds && item.generationId != null,
        ...(keepIds ? {storedDocumentId: document?.id ?? null} : {}),
      }
    })
    for (const {itemId, dependsOnItemId} of procedures.dependencyEntries(dependencies)) {
      const itemTempId = realToTemp.get(itemId)
      const depTempId = realToTemp.get(dependsOnItemId)
      if (itemTempId == null || depTempId == null) continue
      editable.find(i => i.tempId === itemTempId)?.dependsOn.push(depTempId)
    }
    return editable
  }

  /**
   * Reads the chosen template and fills the form from it.
   *
   * <p>The sentence names the template rather than repeating what the server said, because a reader
   * who has just picked one from the list needs to know the form in front of them is still empty.
   * What to do about it, and whether it is worth reporting, still come from the failure itself.
   */
  async function loadTemplate(id: number) {
    try {
      const detail = await procedures.getTemplate(id)
      templateDetail.value = detail
      name.value = detail.template.name
      description.value = detail.template.description ?? ''
      items.value = toEditableItems(detail.items, detail.dependencies, detail.documents, false)
      const templateParty = toRestriction(detail.party)
      if (!isEditMode.value && namesSomebody(templateParty)) party.value = templateParty
    } catch (e) {
      failure.value = {...describeFailure(e, t), message: t('procedures.templateNotLoaded')}
    }
  }

  async function handleTemplateChange(idStr: string | undefined) {
    if (!idStr) {
      selectedTemplateId.value = null
      templateDetail.value = null
      items.value = []
      name.value = ''
      description.value = ''
      party.value = emptyRestriction()
      return
    }
    selectedTemplateId.value = Number(idStr)
    await loadTemplate(Number(idStr))
  }

  const {loading, failure, reload} = useAsyncLoader(async () => {
    const [tpls, choices] = await Promise.all([procedures.getTemplates(), loadPartyChoices()])
    templates.value = tpls.filter(tpl => !tpl.archived)
    partyChoices.value = choices

    if (isEditMode.value) {
      const detail = await procedures.getProcedure(editId.value!)
      name.value = detail.procedure.name
      savedName.value = detail.procedure.name
      description.value = detail.procedure.description ?? ''
      dueAt.value = instantToDate(detail.procedure.dueAt)
      isPublic.value = detail.procedure.isPublic
      about.value = detail.procedure.aboutMemberId == null ? '' : String(detail.procedure.aboutMemberId)
      savedAbout.value = about.value
      party.value = toRestriction(detail.party)
      existingItemIds.value = new Set(detail.items.map(i => i.id))
      items.value = toEditableItems(detail.items, detail.dependencies, detail.documents, true)
      return
    }
    if (presetTemplateId.value != null) {
      selectedTemplateId.value = presetTemplateId.value
      await loadTemplate(presetTemplateId.value)
    }
  }, {autoLoad: false})

  /**
   * Appends a step, empty unless something is handed in. An empty one is a row to write in rather
   * than a step: the list edits every field in place, so asking for the title up front only defers
   * what the row does anyway. Steps still without a title when the form is saved are dropped.
   */
  function addItem(title = '', itemDescription = '') {
    items.value = [...items.value, {
      tempId: nextTempId++,
      title: title.trim(),
      description: itemDescription,
      isPublic: true,
      userAssigned: false,
      position: items.value.length,
      dependsOn: [],
      document: null,
      documentMade: false,
    }]
  }

  /**
   * Removes an item and any dependency on it, so no item is left waiting on something that is
   * no longer in the list.
   */
  function removeItem(index: number) {
    const removed = items.value[index]
    if (!removed) return
    items.value = items.value
      .filter((_, i) => i !== index)
      .map(item => ({...item, dependsOn: item.dependsOn.filter(d => d !== removed.tempId)}))
  }

  function reorderItems(fromIndex: number, toIndex: number) {
    items.value = moveWithin(items.value, fromIndex, toIndex)
  }

  /** Puts an item in place of the one at the given index, as its card hands it back. */
  function updateItem(index: number, item: EditableItem) {
    items.value = items.value.map((current, i) => (i === index ? item : current))
  }

  /** The documents the form starts asking to sign: those of new steps and of steps whose document changed. */
  const changedDocuments = computed(() => items.value
    .filter(item => item.document != null && documentChanged(item.storedDocumentId, item.document))
    .map(item => item.document!))

  /**
   * Why the form cannot be saved as it stands, as the messages to show, each a refusal the server would
   * give: document steps its user may not ask for, documents that are gone, a new procedure about nobody,
   * and documents to sign on a procedure about nobody.
   */
  const blockers = computed(() => {
    const changed = changedDocuments.value
    return [
      changed.length > 0 && !hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER) && 'procedures.stepDocumentsNotYours',
      changed.some(document => document.gone) && 'procedures.stepDocumentsGone',
      aboutRequired.value && !about.value && 'procedures.aboutRequired',
      changed.length > 0 && !about.value && 'procedures.stepDocumentsNeedAbout',
    ].filter((key): key is string => typeof key === 'string')
  })

  function itemPayload(item: EditableItem, position: number): ProcedureItemRequest {
    return {
      title: item.title,
      description: item.description || undefined,
      isPublic: item.isPublic,
      userAssigned: item.userAssigned,
      position,
      ...stepKindChange(item.storedDocumentId, item.document),
    }
  }

  function buildDependencies(tempToReal: Map<number, number>) {
    const deps: StepDependency[] = []
    for (const item of items.value) {
      const realId = tempToReal.get(item.tempId)
      if (!realId) continue
      for (const depTempId of item.dependsOn) {
        const realDepId = tempToReal.get(depTempId)
        if (realDepId) deps.push({itemId: realId, dependsOnItemId: realDepId})
      }
    }
    return deps
  }

  /**
   * Saves the steps and returns the real id of each new one by its temporary id. A row nobody wrote
   * a title into never became a step, so it is dropped rather than saved.
   */
  async function syncItems(pid: number): Promise<Map<number, number>> {
    items.value = items.value.filter(item => item.title.trim())
    const keptIds = new Set(items.value.filter(i => i.id).map(i => i.id!))
    for (const oldId of existingItemIds.value) {
      if (!keptIds.has(oldId)) await procedures.deleteItem(pid, oldId)
    }
    const tempToReal = new Map<number, number>()
    for (const [i, item] of items.value.entries()) {
      if (item.id) {
        await procedures.editItem(pid, item.id, itemPayload(item, i))
        tempToReal.set(item.tempId, item.id)
      } else {
        const created = await procedures.addItem(pid, itemPayload(item, i))
        tempToReal.set(item.tempId, created.id)
      }
    }
    return tempToReal
  }

  /**
   * Persists the form and returns the id of the procedure to navigate to.
   */
  async function submit(): Promise<number> {
    if (isEditMode.value) {
      const pid = editId.value!
      await procedures.updateProcedure(pid, {
        name: name.value.trim(),
        description: description.value || undefined,
        dueAt: dateToInstant(dueAt.value),
        isPublic: isPublic.value,
      })
      if (about.value && about.value !== savedAbout.value) {
        await procedures.setAbout(pid, Number(about.value), false)
        savedAbout.value = about.value
      }
      await procedures.setParty(pid, party.value)
      await procedures.setProcedureDependencies(pid, buildDependencies(await syncItems(pid)))
      return pid
    }

    const created = await procedures.createProcedure({
      name: name.value.trim(),
      description: description.value || undefined,
      dueAt: dateToInstant(dueAt.value) ?? undefined,
      isPublic: isPublic.value,
      party: party.value,
      aboutMemberId: about.value ? Number(about.value) : null,
    })
    const tempToReal = new Map<number, number>()
    for (const [i, item] of items.value.entries()) {
      const createdItem = await procedures.addItem(created.id, itemPayload(item, i))
      tempToReal.set(item.tempId, createdItem.id)
    }
    await procedures.setProcedureDependencies(created.id, buildDependencies(tempToReal))
    return created.id
  }

  return {
    name,
    savedName,
    description,
    dueAt,
    isPublic,
    about,
    aboutFixed,
    aboutRequired,
    templates,
    selectedTemplateId,
    templateDetail,
    partyChoices,
    aboutChoices,
    party,
    items,
    blockers,
    isEditMode,
    loading,
    failure,
    reload,
    handleTemplateChange,
    addItem,
    removeItem,
    reorderItems,
    updateItem,
    submit,
  }
}
