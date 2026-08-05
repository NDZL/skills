package fixtures.rules.positive

/**
 * POSITIVE fixture for MEM-CACHE-001 (BLOCKER).
 *
 * Why this is a defect: the upper bound is set by the CUSTOMER'S data, not by this code. There is
 * no eviction, no size cap, and no release under pressure. The map is held by an object, so it is
 * resident in every process state -- including the restrictive not-visible one.
 *
 * Expected assessment output: MEM-CACHE-001 BLOCKER, with a derived projection table
 * (~166 B/row for Item) and a crossing point expressed in rows.
 */
object ItemRepository {

    private val cache = HashMap<String, Item>()

    fun warmAll(dao: ItemDao) {
        // Loads every row the customer has. 5k in the test fixture, 2M at the largest account.
        for (item in dao.selectAll()) {
            cache[item.sku] = item
        }
    }

    fun find(sku: String): Item? = cache[sku]
}

data class Item(
    val sku: String,          // chars: 24
    val description: String,  // chars: 40
    val price: Double,
    val qtyOnHand: Int,
    val locationId: Long,
    val active: Boolean,
)

interface ItemDao {
    fun selectAll(): List<Item>
}
