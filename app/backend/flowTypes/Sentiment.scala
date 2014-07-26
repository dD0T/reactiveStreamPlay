package backend.flowTypes

/**
 * Message expressing the sentiment of another message.
 *
 * @param uid Unique message ID.
 * @param originUid Message the sentiment was derived from.
 * @param positiveScore Positivity level in the origin message.
 * @param negativeScore Negativity level in the origin message.
 */
case class Sentiment(override val uid: Long, override val originUid: Long, positiveScore: Double, negativeScore: Double) extends FlowObject {
  override def fields(): List[String] = List("positive", "negative", "objective")

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
