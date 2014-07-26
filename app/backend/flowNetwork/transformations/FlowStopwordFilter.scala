package backend.flowNetwork.transformations

import akka.actor.Props
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}
import backend.flowTypes.FlowObject
import play.api.Play

object FlowStopwordFilter {
  var nodeType = "StopwordFilter"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowStopwordFilter(id, name, x, y))
}

/**
 * Filters messages whose FOI is found on a preconfigured stop word list.
 *
 * @param id Unique numeric ID of this actor
 * @param name Display name for this actor
 * @param x X coordinate on screen
 * @param y Y coordinate on screen
 */
class FlowStopwordFilter(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowStopwordFilter.nodeType, x, y, 1, 1) with TargetableFlow with FlowFieldOfInterest {

  val stopWords = Tools.loadWordsetFromFile(Play.current.configuration.getString("stopword.list")) map (_.toLowerCase)

  var dropped: Int = 0

  addConfigMapGetters(() => Map(
    "#entries" -> stopWords.size.toString,
    "dropped" -> dropped.toString,
    "display" -> "#entries,dropped"
  ))

  override def active: Receive = {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(value) =>
          if (!(stopWords contains value.toLowerCase)) target ! o
          else {
            dropped += 1
            configUpdated()
          }
        case None => log.debug(s"Message ${o.uid} doesn't have a String convertible field $fieldOfInterest")
      }
  }
}
