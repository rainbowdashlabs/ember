/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventRegistrationFieldValue} from '@/api/generated/schema'
import {eventCourses, partnerCourses} from '@/api'

/**
 * Where a course page sends its sign-ups: the station holding the course, which is either this one or
 * a partner sharing it. The page and its dialogs read the same either way.
 */
export interface CourseSignUpPort {
    signUp(parts: number[], answers: EventRegistrationFieldValue[], memberId?: number): Promise<unknown>
    changeParts(parts: number[], memberId?: number): Promise<unknown>
    withdraw(memberId?: number): Promise<unknown>
}

/** Sign-ups for a course of this station. */
export function ownCoursePort(courseId: number): CourseSignUpPort {
    return {
        signUp: (parts, answers, memberId) => eventCourses.signUp(courseId, parts, answers, memberId),
        changeParts: (parts, memberId) => eventCourses.changeParts(courseId, parts, memberId),
        withdraw: memberId => eventCourses.withdraw(courseId, memberId),
    }
}

/** Sign-ups for a course a partner station shares. Partners ask no questions of visitors. */
export function partnerCoursePort(stationUid: string, courseId: number): CourseSignUpPort {
    return {
        signUp: (parts, _answers, memberId) => partnerCourses.signUp(stationUid, courseId, parts, memberId),
        changeParts: (parts, memberId) => partnerCourses.changeParts(stationUid, courseId, parts, memberId),
        withdraw: memberId => partnerCourses.withdraw(stationUid, courseId, memberId),
    }
}
