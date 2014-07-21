package backend.flowNetwork.transformations

import scala.io.Source

object Tools {
  def loadWordsetFromFile(configFile: Option[String]): Set[String] = configFile match {
    case Some(file) => try {
      (Source.fromFile(file, "UTF-8")
        .getLines() filter (l => !(l startsWith ";") && !(l isEmpty))).toSet
    } catch {
      case e:Exception =>
        println(s"Failed to load $file because of $e")
        Set()
    }
    case None => Set()
  }
}
