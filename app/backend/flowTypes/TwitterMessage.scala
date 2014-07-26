package backend.flowTypes

/**
 * Message containing a twitter status update.
 *
 * @param uid Message ID (!= twitter message ID).
 * @param name Display name of the poster.
 * @param message Message in the status update.
 * @param lang Language of the status update.
 */
case class TwitterMessage(override val uid: Long, name: String, message: String, lang: String) extends FlowObject {
  override def content(field: String): Option[Any] = field match {
    case "default" | "message" => Some(message)
    case "name" => Some(name)
    case "lang" => Some(lang)
    case _ => None
  }

  override def fields(): List[String] = List("message", "name", "lang")

  override val originUid: Long = -1

}
