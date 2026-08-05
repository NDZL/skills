package fixtures.rules.negative

/**
 * NEGATIVE fixture for MEM-CACHE-001. This must NOT be reported as a defect.
 *
 * It will MATCH the search signature -- a long-lived map in an object -- which is exactly the
 * point. The scanner reports candidates; the assessment must then read the surrounding code and
 * REJECT this one.
 *
 * Why it is not a defect: the bound is set by THIS CODE, not by customer data. There are seven
 * entries and there will only ever be seven, because the enum is the domain. Total cost is a few
 * hundred bytes and cannot grow.
 *
 * The test that decides it: "is the upper bound set by our code or by the customer's data?"
 * Only the latter is a defect. Flagging this is anti-pattern AP-05 (flagging bounded data as
 * unbounded), and it is how a memory report earns enough distrust to be switched off.
 */
enum class PickStatus { NEW, ASSIGNED, IN_PROGRESS, SHORT, COMPLETE, CANCELLED, ON_HOLD }

object StatusLabels {

    // Deliberately a mutable HashMap populated in a loop, so it MATCHES the MEM-CACHE-001 search
    // signature exactly as an unbounded cache would. That is the point of this fixture: the regex
    // cannot tell the difference, so the judgement has to.
    //
    // Bounded by the enum, not by any data set. Seven entries, forever, a few hundred bytes total.
    private val labels = HashMap<PickStatus, String>()

    init {
        for (status in PickStatus.values()) {
            labels[status] = when (status) {
                PickStatus.NEW -> "New"
                PickStatus.ASSIGNED -> "Assigned"
                PickStatus.IN_PROGRESS -> "In progress"
                PickStatus.SHORT -> "Short"
                PickStatus.COMPLETE -> "Complete"
                PickStatus.CANCELLED -> "Cancelled"
                PickStatus.ON_HOLD -> "On hold"
            }
        }
    }

    fun label(status: PickStatus): String = labels.getValue(status)
}
