package backend.flowNetwork.sinks

import akka.actor.Props
import backend.flowNetwork.{FlowFieldOfInterest, FlowNode}
import backend.flowTypes.FlowObject
import play.api.libs.json.Json

import scala.util.Sorting

object FlowFrequency {
  var nodeType = "Frequency"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowFrequency(id, name, x, y))
}

/**
 * Maintains a toplist of the most frequently seen values in the FOI.
 *
 * @param id Unique numeric ID of this actor
 * @param name Display name for this actor
 * @param x X coordinate on screen
 * @param y Y coordinate on screen
 */
class FlowFrequency(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowFrequency.nodeType, x, y, 0, 1) with FlowFieldOfInterest {

  /** Frequency counters */
  var frequencies = scala.collection.mutable.HashMap[String, Int]() withDefaultValue 0
  var toplist = scala.collection.mutable.Buffer[(String, Int)]()
  var toplistLimit = 10 // For now uses a fixed top ten list

  var total = 0

  def toplistAsJsonString: String =
    Json.toJson(toplist map { case (o,n) => Json.obj("value" -> o.toString, "count" -> n.toString) }).toString

  addConfigMapGetters(() => Map(
    "active" -> "1",
    "toplist" -> toplistAsJsonString,
    "toplistLimit" -> toplistLimit.toString,
    "total" -> total.toString,
    "display" -> "toplist,total"
  ))

  addConfigSetters({
    case ("toplistLimit", n) =>
      log.info(s"Setting toplist size set to $n")
      toplistLimit = n.toInt

      while (toplist.size > toplistLimit) {
        toplist.remove(toplist.size - 1)
      }

      //FIXME: Growing is broken but it's not really that important
  })

  override def receive: Receive = super.receive orElse {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(content) => {
          // Increase frequency count
          frequencies(content) += 1
          val n = frequencies(content)
          total += 1

          // Update toplist
          if (toplist.size < toplistLimit || toplist.last._2 < n) {
            // Object enters toplist or is already there. Try to find it to...
            toplist.zipWithIndex.find({ case ((obj, _), _) => obj == content}) match {
              case Some((_, idx)) =>
                toplist(idx) = (content, n) // update or...
              case None => {
                if (toplist.size < toplistLimit) {
                  toplist.append((content, n))
                } else {
                  toplist(toplistLimit - 1) = (content, n) // if not found replace the last
                }
              }
            }

            // New toplist, resort and distribute
            toplist = Sorting.stableSort(toplist, (A: (Any, Int), B: (Any, Int)) => (A, B) match {
              case ((_, a), (_, b)) if a > b => true
              case _ => false
            }).toBuffer
          }

          configUpdated() // Total changed
        }
        case None => log.debug(s"Message ${o.uid} doesn't have field $fieldOfInterest")
      }
  }
}
