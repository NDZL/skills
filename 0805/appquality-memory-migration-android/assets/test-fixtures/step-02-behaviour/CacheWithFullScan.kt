package fixtures.step02

/*
 * FIXTURE for STEP-02 -- the behaviour-preservation refusal case.
 *
 * The cache below IS unbounded and its size IS driven by customer data, so it matches the
 * MEM-CACHE-001 signature and looks like a textbook STEP-02 target.
 *
 * EXPECTED SKILL BEHAVIOUR: notice `totalStockValue()` and `activeSkus()`, which iterate over ALL
 * values, and do NOT silently bound the cache. Bounding it would make those two functions return
 * wrong answers -- silently, and only at customer scale, where the eviction actually starts.
 *
 * The correct response is to surface the conflict and let the developer choose: move the aggregate
 * into a database query (which fixes both problems at once), or keep the structure and accept the
 * memory cost. That decision is the developer's, not the skill's.
 *
 * Applying the bound anyway produces correct-looking memory numbers plus a functional regression --
 * anti-pattern AP-09, and the worst trade available in an enterprise workflow.
 */

object PriceBook {

    // Unbounded, customer-scaled. A genuine MEM-CACHE-001 candidate.
    private val items = HashMap<String, Item>()

    fun load(dao: ItemDao) {
        for (item in dao.selectAll()) items[item.sku] = item
    }

    fun find(sku: String): Item? = items[sku]

    // ---- These two depend on the cache holding EVERYTHING. Bounding it breaks them. ----

    /** Used by the end-of-shift summary screen. */
    fun totalStockValue(): Double = items.values.sumOf { it.price * it.qtyOnHand }

    /** Used to build the offline picklist. */
    fun activeSkus(): List<String> = items.values.filter { it.active }.map { it.sku }
}

data class Item(
    val sku: String,
    val description: String,
    val price: Double,
    val qtyOnHand: Int,
    val active: Boolean,
)

interface ItemDao {
    fun selectAll(): List<Item>
}

/*
 * The database-query alternative the skill should propose instead of a bound:
 *
 *   @Query("SELECT SUM(price * qtyOnHand) FROM items")
 *   fun totalStockValue(): Double
 *
 *   @Query("SELECT sku FROM items WHERE active = 1")
 *   fun activeSkus(): List<String>
 *
 * That removes the aggregate's dependency on full residency, which then makes STEP-02 applicable
 * without any behaviour change -- and converts anonymous bytes into clean file-backed pages.
 */
