/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {PageTargetKind} from '@/api/forms'
import {
    FormQuestionType,
    StationUserType,
    type FormLayout,
    type FormPage,
    type FormQuestion,
    type FormQuestionConfig,
    type MemberGroup,
    type QuestionBranch,
    type UserTag,
} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'
import type {MemberLike} from '@/components/input/select/memberOption'
import type {Translate} from '@/util/failure'
import {numberedOptions} from '@/util/formOptions'
import {useFormLayout, type FormLayoutEditor} from '@/views/stationview/forms/builderview/useFormLayout'

const FORM_ID = 1
const STATION = 'wache'
const FIRST_PAGE = 'p0'

/**
 * A form of three pages: the first asks a rating, a choice and whether the reader comes along, and
 * that last answer leads to the second page or the third.
 */
export function sampleLayout(t: Translate): FormLayout {
    return {
        pages: [
            page(0, FIRST_PAGE, ''),
            page(1, 'p1', 'Mitfahrt'),
            page(2, 'p2', 'Warum nicht?'),
        ],
        questions: [
            question(1, t('helpCenter.sample.forms.satisfaction'), {questionType: FormQuestionType.RATING, scale: 5, icon: 'STAR'},
                '1 = sehr unzufrieden, 5 = sehr zufrieden'),
            question(2, t('helpCenter.sample.forms.likedMost'), {
                questionType: FormQuestionType.CHOICE,
                multiSelect: true,
                multiLimitType: 'AT_MOST',
                multiLimit: 2,
                allowOther: true,
                options: numberedOptions(t('helpCenter.formsFill.dummyChoiceOption1'), t('helpCenter.formsFill.dummyChoiceOption2')),
            }),
            question(3, t('helpCenter.formsPages.dummyBranchQuestion'), {
                questionType: FormQuestionType.CHOICE,
                options: numberedOptions(t('helpCenter.formsPages.dummyBranchYes'), t('helpCenter.formsPages.dummyBranchNo')),
            }, '', {o0: {kind: PageTargetKind.PAGE, page: 'p1'}, o1: {kind: PageTargetKind.PAGE, page: 'p2'}}),
        ],
    }
}

/** An editor holding the sample form, the way the form editor holds the one it edits. */
export function sampleLayoutEditor(t: Translate): FormLayoutEditor {
    const editor = useFormLayout()
    editor.load(sampleLayout(t))
    return editor
}

/** The groups a form can be limited to. */
export function sampleGroups(t: Translate): MemberGroup[] {
    return [
        {id: 1, stationId: STATION, name: t('helpCenter.sample.groups.beginners'), color: null, position: 0, groupSetId: null, userTypes: []},
        {id: 2, stationId: STATION, name: t('helpCenter.sample.groups.advanced'), color: null, position: 1, groupSetId: null, userTypes: []},
    ]
}

/** The tags a form can be limited to. */
export function sampleTags(t: Translate): UserTag[] {
    return [{id: 1, stationId: STATION, name: t('helpCenter.sample.groups.competitionGroup'), color: null, visible: true, position: 0}]
}

/** The members a form can be limited to. */
export function sampleMembers(): MemberLike[] {
    return [{id: 1, name: 'Max Mustermann'}, {id: 2, name: 'Erika Musterfrau'}]
}

/** A form open to members of the beginners group. */
export function sampleRestriction(): RestrictionSelection {
    return {userTypes: [StationUserType.MEMBER], groupIds: [1], tagIds: [], memberIds: [], mode: 'OR'}
}

function page(position: number, key: string, title: string): FormPage {
    return {id: position + 1, formId: FORM_ID, key, position, title, description: '', after: {kind: PageTargetKind.NEXT}}
}

function question(id: number, title: string, config: FormQuestionConfig, description = '', branch: QuestionBranch | null = null): FormQuestion {
    return {
        id, formId: FORM_ID, pageKey: FIRST_PAGE, position: id, title, description, required: true, shuffle: false,
        formQuestionType: config.questionType, config, branch,
    }
}
