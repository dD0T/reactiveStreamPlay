package backend.flowTypes

trait FlowObject {
  /** Unique identifier of this object instance */
  val uid:Long
  /** Unique identifier of the object this one was created from. -1 if none */
  val originUid:Long

  /** Retrieves the content of the given field. None if invalid field */
  def content(field:String = "default"): Option[Any]

  /** Typesafe field content retrieval with implicit conversion for select types to String. */
  def contentAsString(field:String = "default"): Option[String] = content(field) match {
    case Some(s) => Some(s.toString())
    case _ => None
  }

  /** Typesafe field content retrieval with implicit conversion for select types to Int. */
  def contentAsInt(field:String = "default") : Option[Int] = content(field) match {
    case Some(i:Int) => Some(i)
    case Some(s:String) => try { Some(s.toInt) } catch { case _: Throwable => None }
    case Some(n:Double) => Some(n.toInt)
    case _ => None
  }

  /** Typesafe field content retrieval with implicit conversion for select types to Double. */
  def contentAsDouble(field:String = "default") : Option[Double] = content(field) match {
    case Some(n:Double) => Some(n)
    case Some(i:Int) => Some(i.toDouble)
    case Some(s:String) => try { Some(s.toDouble) } catch { case _: Throwable => None }
    case _ => None
  }

  override def toString(): String = s"${this.getClass.getSimpleName}: default=${content()}}"

  /** List of fields available from content functions */
  def fields(): List[String]

  /**
   * Returns a String->String map representation of the flow object.
   */
  def asStringMap(): Map[String, String] =
    Map("uid" -> uid.toString,
        "originUid" -> originUid.toString
    ) ++ (fields map {f => (f, contentAsString(f).get) }).toMap
}
