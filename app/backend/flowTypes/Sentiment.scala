package backend.flowTypes

case class Sentiment(override val uid: Long, override val originUid: Long, sentiment: Double) extends FlowObject {
  override def fields(): List[String] = List("default", "sentiment")

  override def content(field: String): Option[Any] = field match {
    case "default" | "sentiment" => Some(sentiment)
    case _ => None
  }
}

object Sentiment {
  def apply(uid: Long, source: FlowObject, sentiment: Double) = new Sentiment(uid, source.uid, sentiment)
}
