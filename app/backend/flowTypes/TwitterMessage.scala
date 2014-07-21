package backend.flowTypes

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
