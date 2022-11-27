package app.familygem.constant.intdefs

import androidx.annotation.IntDef

@IntDef(NOTHING, OBJ_2_ADDED, OBJ_2_REPLACES_OBJ, OBJ_DELETED)
@Retention(AnnotationRetention.SOURCE)
/**
 * what to do with this pair of objects:
 * 0 nothing
 * 1 object2 is added to the tree
 * 2 object2 replaces object
 * 3 object is deleted
 *
 * che fare di questa coppia di oggetti:
 * 0 niente
 * 1 object2 viene aggiunto ad albero
 * 2 object2 sostituisce object
 * 3 object viene eliminato
 */
annotation class Destiny

const val NOTHING = 0
const val OBJ_2_ADDED = 1
const val OBJ_2_REPLACES_OBJ = 2
const val OBJ_DELETED = 3
val Int.isObj1Removed get() = this == OBJ_2_REPLACES_OBJ || this == OBJ_DELETED
val Int.isObj2Added get() = this == OBJ_2_REPLACES_OBJ || this == OBJ_2_ADDED


