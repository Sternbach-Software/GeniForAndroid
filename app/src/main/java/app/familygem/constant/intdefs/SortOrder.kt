package app.familygem.constant.intdefs

import androidx.annotation.IntDef

@IntDef(UNDEFINED_SORT_ORDER, SORT_BY_ID, SORT_BY_NAME, SORT_BY_SOURCE_COUNT)
@Retention(AnnotationRetention.SOURCE)
annotation class SortOrder
const val UNDEFINED_SORT_ORDER = 0
const val SORT_BY_ID = 1
const val SORT_BY_NAME = 2
const val SORT_BY_SOURCE_COUNT = 3
