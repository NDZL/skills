package fixtures.step01.after

/*
 * FIXTURE for STEP-01 -- the "after" side. Compare with before.kt in this directory.
 *
 * Peak is now proportional to BATCH_SIZE rather than to the response. No raw String, no parse tree,
 * no transfer object graph: each object is read, converted, and handed to a batch that is flushed
 * and cleared.
 *
 * THE ASSERTION FOR THIS PAIR IS BEHAVIOUR, NOT MEMORY. The same rows, with the same values, must
 * end up stored. The memory improvement needs a device to demonstrate; claiming it from the diff
 * alone is anti-pattern AP-05.
 *
 * What changed, and nothing else:
 *   - the read path, from string-then-parse to a streaming read
 *   - writes are batched inside a transaction, so a failed sync leaves no partial data
 *
 * What deliberately did NOT change:
 *   - the stored schema and values
 *   - the caller's dispatcher
 *   - error propagation to the caller
 */

import android.util.JsonReader
import java.io.InputStream
import java.io.InputStreamReader

private const val BATCH_SIZE = 500

class ItemSync(
    private val api: ItemApi,
    private val dao: ItemDao,
) {

    suspend fun sync() {
        api.openItemsStream().use { stream ->
            streamInto(stream)
        }
    }

    private suspend fun streamInto(stream: InputStream) {
        val batch = ArrayList<ItemEntity>(BATCH_SIZE)

        JsonReader(InputStreamReader(stream, "UTF-8")).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                batch += readItem(reader)
                if (batch.size >= BATCH_SIZE) {
                    dao.insertBatchInTransaction(batch)
                    batch.clear()
                }
            }
            reader.endArray()
        }

        if (batch.isNotEmpty()) {
            dao.insertBatchInTransaction(batch)
            batch.clear()
        }
    }

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
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return ItemEntity(sku, description, price, qtyOnHand)
    }
}

data class ItemEntity(
    val sku: String,
    val description: String,
    val price: Double,
    val qtyOnHand: Int,
)

interface ItemApi {
    suspend fun openItemsStream(): InputStream
}

interface ItemDao {
    suspend fun insertBatchInTransaction(items: List<ItemEntity>)
}
