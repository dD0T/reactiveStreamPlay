package backend

/** Generates a unique ID for a FlowObject.
  *
  * Obviously very broken in a multitude of ways.
  * Mon-monotonic time source, synchronized, not distributable, ...
  * Will do for this toy project though. Real solutions to this
  * (UUID, Snowflake, ...) are pretty painful to use and really
  * overkill as long as this isn't used in production somewhere.
  */
object NextFlowUID {
  private var lastUid: Long = System.currentTimeMillis()

  def apply(): Long = synchronized {
    var uid = System.currentTimeMillis()
    if (uid <= lastUid ) {
      uid = lastUid + 1
    }

    lastUid = uid
    uid
  }
}