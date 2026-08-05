package patterns.streaming

/*
 * STEP-01 -- stream a whole-response parse into the database.
 *
 * Validation level: Inspection. Not compiled or measured in this authoring pass.
 * License basis: Apache-2.0 proposed, not confirmed. Owner: UNASSIGNED.
 *
 * WHAT THIS REPLACES
 *
 *   // Peak holds ALL of these at once:
 *   val body = response.body!!.string()            // raw string
 *   val dtos = gson.fromJson<List<ItemDto>>(body)  // parser structures + DTO graph
 *   dao.insertAll(dtos.map { it.toEntity() })      // + a second mapped graph
 *
 * Peak is proportional to the whole response. After this pattern it is proportional to BATCH_SIZE.
 *
 * WHY IT WORKS: the string and both object graphs are ANONYMOUS memory -- compressible into zRAM but
 * never droppable. Database pages are clean and file-backed, so the kernel can drop them for free.
 * The step converts expensive bytes into cheap ones, which is why it beats merely parsing faster.
 *
 * MUST NOT CHANGE: the stored result. Same rows, same values.
 * WATCH FOR: failure timing. A partial parse can leave partial data, so each batch is a transaction.
 * THREADING: keep the caller's dispatcher; do not introduce one here.
 */

import android.util.JsonReader
import java.io.InputStreamReader

private const val BATCH_SIZE = 500

/** Streams items from [stream] straight into [dao], holding at most [BATCH_SIZE] in memory. */
suspend fun syncItems(stream: java.io.InputStream, dao: ItemDao) {
    val batch = ArrayList<ItemEntity>(BATCH_SIZE)

    JsonReader(InputStreamReader(stream, "UTF-8")).use { reader ->
        reader.beginArray()
        while (reader.hasNext()) {
            batch += readItem(reader)
            if (batch.size >= BATCH_SIZE) {
                dao.insertBatchInTransaction(batch)   // all-or-nothing
                batch.clear()                         // release before reading more
            }
        }
        reader.endArray()
    }

    if (batch.isNotEmpty()) {
        dao.insertBatchInTransaction(batch)
        batch.clear()
    }
}

/** Reads exactly one object. No intermediate transfer object is allocated. */
private fun readItem(reader: JsonReader): ItemEntity {
    var sku = ""
    var description = ""
    var price = 0.0
    var qtyOnHand = 0

    reader.beginObject()
    while (reader.hasNext()) {
        when (reader.nextName()) {
            "sku" -> sku = reader.nextString()
            "description" -> description = reader.nextString()
            "price" -> price = reader.nextDouble()
            "qtyOnHand" -> qtyOnHand = reader.nextInt()
            else -> reader.skipValue()   // never materialise fields we do not store
        }
    }
    reader.endObject()

    return ItemEntity(sku, description, price, qtyOnHand)
}

/*
 * Room side. The transaction is what makes a failed sync leave no partial data.
 *
 *   @Dao interface ItemDao {
 *       @Insert(onConflict = OnConflictStrategy.REPLACE)
 *       suspend fun insertAll(items: List<ItemEntity>)
 *
 *       @Transaction
 *       suspend fun insertBatchInTransaction(items: List<ItemEntity>) = insertAll(items)
 *   }
 *
 * VERIFY: peak RssAnon during a full sync, sampled in the NOT-VISIBLE state (press Home while the
 * sync runs) -- that is the tighter ceiling and the one a background sync actually runs under.
 *
 * If the project has no streaming parser available, do NOT add a dependency without approval:
 * a new dependency is itself memory. Most stacks already expose a streaming source or reader.
 */

data class ItemEntity(
    val sku: String,
    val description: String,
    val price: Double,
    val qtyOnHand: Int,
)

interface ItemDao {
    suspend fun insertBatchInTransaction(items: List<ItemEntity>)
}
