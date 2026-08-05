package patterns.cache

/*
 * STEP-02 -- bound a cache, or move it to the database.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * WHAT THIS REPLACES
 *
 *   object ItemRepository {
 *       private val cache = HashMap<String, Item>()   // grows with the CUSTOMER's data
 *       fun warmAll(dao: ItemDao) { for (i in dao.selectAll()) cache[i.sku] = i }
 *   }
 *
 * At roughly 166 B/row that is 0.8 MB against a 5 000-row test fixture and 332 MB at two million
 * rows -- a 400x span, which is exactly why this defect always passes in development.
 *
 * TWO VALID FIXES. Option B is usually better.
 *
 *   Option A -- bound it.        Caps anonymous memory at a size YOU choose.
 *   Option B -- query the DB.    Converts anonymous bytes into clean file-backed pages the kernel
 *                               can drop for free, and usually deletes code.
 *
 * BEFORE APPLYING, CHECK: did anything depend on the cache holding EVERYTHING? An iteration over
 * all values, or a size check, will break. That is anti-pattern AP-09.
 *
 * DECISION NEEDED FROM THE DEVELOPER: the size for Option A, or agreement to use Option B.
 * Do not choose a cache size unilaterally.
 */

import android.app.ActivityManager
import android.util.LruCache

// ---------------------------------------------------------------- Option A: bounded

class BoundedItemCache(activityManager: ActivityManager, private val dao: ItemDao) {

    // Tier from what the device reports, never from a model string. A 1 GB wearable and an 8 GB
    // handheld must not get the same budget from one binary.
    private val maxEntries: Int =
        if (activityManager.isLowRamDevice || activityManager.memoryClass <= 128) 500 else 5_000

    private val cache = object : LruCache<String, Item>(maxEntries) {
        // Entry count is the unit here. Use sizeOf() instead if entries vary widely in size.
        override fun sizeOf(key: String, value: Item): Int = 1
    }

    /** Same observable behaviour as the unbounded version: a hit, or a load. */
    fun find(sku: String): Item? =
        cache.get(sku) ?: dao.selectBySku(sku)?.also { cache.put(sku, it) }

    /** Call from the pressure handler -- see STEP-05. Must be safe to call repeatedly. */
    fun trim(aggressive: Boolean) {
        if (aggressive) cache.evictAll() else cache.trimToSize(maxEntries / 4)
    }
}

// ---------------------------------------------------------------- Option B: no cache

/*
 * Preferred where the query is cheap. The database's own page cache is file-backed and
 * reclaimable, so this is not "no caching" -- it is caching in the cheap kind of memory.
 *
 *   class ItemRepository(private val dao: ItemDao) {
 *       fun find(sku: String): Item? = dao.selectBySku(sku)
 *   }
 *
 *   @Dao interface ItemDao {
 *       // Project only the columns actually read; SELECT * materialises every column per row.
 *       @Query("SELECT sku, description, price, qtyOnHand FROM items WHERE sku = :sku LIMIT 1")
 *       fun selectBySku(sku: String): Item?
 *   }
 *
 * Ensure the lookup column is indexed, or Option B trades memory for a table scan.
 *
 * VERIFY: steady-state RssAnon after the data-heavy screen, before and after. Compare against the
 * plan's derived per-row cost times the REAL record count -- not the fixture count.
 */

data class Item(
    val sku: String,
    val description: String,
    val price: Double,
    val qtyOnHand: Int,
)

interface ItemDao {
    fun selectBySku(sku: String): Item?
}
