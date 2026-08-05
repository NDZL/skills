package fixtures.step01

/*
 * FIXTURE for STEP-01 -- the "before" side of a normal apply-and-verify pair.
 *
 * Four things are resident simultaneously at peak:
 *   1. `body`  -- the raw response as a String
 *   2. the parser's internal structures while building its tree
 *   3. `dtos`  -- the transfer object graph
 *   4. the mapped entity graph passed to insertAll
 *
 * All four are ANONYMOUS memory: compressible into zRAM but never droppable. Peak is proportional to
 * the whole response, so it scales with the customer's catalogue rather than with anything this code
 * controls.
 *
 * Expected pairing: after.kt in this directory, which must store EXACTLY the same rows.
 */

class ItemSync(
    private val api: ItemApi,
    private val dao: ItemDao,
    private val json: JsonParser,
) {

    suspend fun sync() {
        val body = api.fetchItems().body()!!.string()          // 1
        val dtos = json.parseList(body, ItemDto::class.java)   // 2 and 3
        dao.insertAll(dtos.map { it.toEntity() })              // 4
    }
}

data class ItemDto(
    val sku: String,
    val description: String,
    val price: Double,
    val qtyOnHand: Int,
) {
    fun toEntity() = ItemEntity(sku, description, price, qtyOnHand)
}

data class ItemEntity(
    val sku: String,
    val description: String,
    val price: Double,
    val qtyOnHand: Int,
)

interface ItemApi {
    suspend fun fetchItems(): Response
}

interface Response {
    fun body(): Body?
}

interface Body {
    fun string(): String
}

interface JsonParser {
    fun <T> parseList(text: String, type: Class<T>): List<T>
}

interface ItemDao {
    suspend fun insertAll(items: List<ItemEntity>)
}
