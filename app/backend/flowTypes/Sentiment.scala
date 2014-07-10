package backend.flowTypes

case class Sentiment(override val uid: Long, override val originUid: Long, positiveScore: Double, negativeScore: Double) extends FlowObject {
  override def fields(): List[String] = List("default", "sentiment")

  override def content(field: String): Option[Any] = field match {
    case "default" | "sentiment" => Some(positiveScore - negativeScore)
    case "positive" => Some(positiveScore)
    case "negative" => Some(negativeScore)
    case "objective" => Some(1.0 - (positiveScore - negativeScore))
    case _ => None
  }
}

object Sentiment {
  def apply(uid: Long, source: FlowObject,  positiveScore: Double, negativeScore: Double) = new Sentiment(uid, source.uid, positiveScore, negativeScore)
}
