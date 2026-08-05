package fixtures.sizing

/**
 * Sizing fixture. Expected derived cost: see expected.json in this directory.
 *
 * String lengths are declared as comment hints so the estimator does not have to assume them.
 * In a real project these come from the database schema column widths, never from test data --
 * a customer-controlled string length can move the whole projection by an order of magnitude.
 */
data class Item(
    val sku: String,          // chars: 24
    val description: String,  // chars: 40
    val price: Double,
    val qtyOnHand: Int,
    val locationId: Long,
    val active: Boolean,
)
