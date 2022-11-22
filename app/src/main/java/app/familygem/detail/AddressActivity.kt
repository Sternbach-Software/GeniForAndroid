package app.familygem.detail

import app.familygem.DetailActivity
import app.familygem.R
import app.familygem.Memory
import app.familygem.U
import org.folg.gedcom.model.Address

class AddressActivity : DetailActivity() {
    lateinit var a: Address
    override fun format() {
        setTitle(R.string.address)
        placeSlug("ADDR")
        a = cast(Address::class.java) as Address
        place(
            getString(R.string.value),
            "Value",
            false,
            true
        ) // Strongly deprecated in favor of the fragmented address //Fortemente deprecato in favore dell'indirizzo frammentato
        place(getString(R.string.name), "Name", false, false) // _name non standard
        place(getString(R.string.line_1), "AddressLine1")
        place(getString(R.string.line_2), "AddressLine2")
        place(getString(R.string.line_3), "AddressLine3")
        place(getString(R.string.postal_code), "PostalCode")
        place(getString(R.string.city), "City")
        place(getString(R.string.state), "State")
        place(getString(R.string.country), "Country")
        placeExtensions(a)
    }

    override fun delete() {
        deleteAddress(Memory.getSecondToLastObject())
        U.updateChangeDate(Memory.firstObject())
        Memory.setInstanceAndAllSubsequentToNull(a)
    }
}