package backend.flowTypes

/**
 * Message representing a single word.
 *
 * @param uid Unique ID
 * @param originUid Message this word was derived from.
 * @param word Word as a string.
 */
case class WordObject(override val uid: Long, override val originUid: Long, word: String) extends FlowObject {
  override def content(field: String): Option[Any] = field match {
    case "default" | "word" => Some(word)
    case _ => None
  }

  override def fields(): List[String] = List("word")
}

object WordObject {
  def apply(uid: Long, source: FlowObject, word: String) = new WordObject(uid, source.uid, word)
}
