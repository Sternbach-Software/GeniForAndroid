package app.familygem.constant.intdefs

import androidx.annotation.IntDef

@IntDef(
    SHARED_NOTE,
    SUBMITTER,
    REPOSITORY,
    SHARED_MEDIA_TYPE,
    SOURCE_TYPE,
    PERSON_TYPE,
    FAMILY_TYPE
)
@Retention(AnnotationRetention.SOURCE)
annotation class Type

const val SHARED_NOTE = 1
const val SUBMITTER = 2
const val REPOSITORY = 3
const val SHARED_MEDIA_TYPE = 4
const val SOURCE_TYPE = 5
const val PERSON_TYPE = 6
const val FAMILY_TYPE = 7