package backend.flowNetwork.transformations

import akka.actor.Props
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}
import backend.flowTypes.FlowObject
import play.api.Play

object FlowStopwordFilter {
  var nodeType = "StopwordFilter"
  val stopWords = Tools.loadWordsetFromFile(Play.current.configuration.getString("stopword.list")) map (_.toLowerCase)

  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowStopwordFilter(id, name, x, y))
}

class FlowStopwordFilter(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowStopwordFilter.nodeType, x, y, 1, 1) with TargetableFlow with FlowFieldOfInterest {

  var dropped: Int = 0

  addConfigMapGetters(() => Map(
    "#entries" -> FlowStopwordFilter.stopWords.size.toString,
    "dropped" -> dropped.toString,
    "display" -> "#entries,dropped"
  ))

  override def active: Receive = {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(value) =>
          if (!(FlowStopwordFilter.stopWords contains value.toLowerCase)) target ! o
          else {
            dropped += 1
            configUpdated()
          }
        case None => log.debug(s"Message ${o.uid} doesn't have a String convertible field $fieldOfInterest")
      }
  }
}
