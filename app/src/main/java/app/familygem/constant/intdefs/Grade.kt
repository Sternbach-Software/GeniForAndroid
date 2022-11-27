package app.familygem.constant.intdefs

import androidx.annotation.IntDef

@IntDef(
    NEW_FROM_ITALY,
    FOR_SHARING_TO_MARK_PASSED,
    SHARED_FROM_AUSTRALIA,
    RETURNED_TO_ITALY_IS_DERIVATIVE,
    NO_NOVELTIES
)
@Retention(AnnotationRetention.SOURCE)
annotation class Grade

const val NEW_FROM_ITALY = 0
const val FOR_SHARING_TO_MARK_PASSED = 9
const val SHARED_FROM_AUSTRALIA = 10
const val RETURNED_TO_ITALY_IS_DERIVATIVE = 20
const val NO_NOVELTIES = 30
