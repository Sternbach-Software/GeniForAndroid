package app.familygem.constant

import org.folg.gedcom.model.Family
import org.folg.gedcom.model.EventFact

/**
 * Family situation
 */
enum class Status {
    /**
     * Generic relationship
     * */
    NONE,
    MARRIED,
    DIVORCED,
    SEPARATED;

    companion object {
        /**
         * Finds the status of [family]
         */
        @JvmStatic
        fun getStatus(family: Family?): Status {
            var status = NONE
            if (family != null) {
                for (event in family.eventsFacts) {
                    when (event.tag) {
                        "MARR" -> {
                            val type = event.type
                            status =
                                if (type == null || type.isEmpty() || type == "marriage" || type == "civil" || type == "religious" || type == "common law") MARRIED else NONE
                        }
                        "MARB", "MARC", "MARL", "MARS" -> status = MARRIED
                        "DIV" -> status = if (status == MARRIED) DIVORCED else SEPARATED
                    }
                }
            }
            return status
        }
    }
}