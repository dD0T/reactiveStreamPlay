package backend.flowTypes

/**
 * Represents a
 * @param uid
 * @param originUid
 * @param number
 */
case class NumberObject(override val uid: Long, override val originUid: Long, number: Double) extends FlowObject {
  override def content(field: String): Option[Any] = field match {
    case "default" | "number" => Some(number)
    case _ => None
  }

  override def fields(): List[String] = List("number")
}

object NumberObject {
  def apply(uid: Long, source: FlowObject, number: Double) = new NumberObject(uid, source.uid, number)
}
