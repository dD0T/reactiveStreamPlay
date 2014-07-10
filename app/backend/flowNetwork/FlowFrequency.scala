package backend.flowNetwork

import akka.actor.Props
import backend.flowTypes.FlowObject

import scala.util.Sorting

object FlowFrequency {
  def props(): Props = Props(new FlowFrequency)
}


case class FlowFrequencyUpdate(override val uid: Long, override val originUid: Long, number: Double) extends FlowObject {
  override def content(field: String): Option[Any] = field match {
    case "default" | "number" => Some(number)
    case _ => None
  }

  override def fields(): List[String] = List("default", "number")
}

object FlowFrequencyUpdate {
  def apply(uid: Long, source: FlowObject, number: Double) = new FlowFrequencyUpdate(uid, source.uid, number)
}



class FlowFrequency extends TargetableFlow with FlowFieldOfInterest {
  var frequencies = scala.collection.mutable.HashMap[Any, Int]() withDefaultValue 0

  var toplist = scala.collection.mutable.Buffer[(Any, Int)]()
  val MAX_TOPLIST_SIZE = 10 // For now uses a fixed top ten list

  var total = 0

  def common: Receive = handleFieldOfInterest

  override def passive: Receive = common
  override def active: Receive = common orElse {
    case o: FlowObject =>
      o.content(fieldOfInterest) match {
        case Some(thing) => {
          frequencies(thing) += 1
          val n = frequencies(thing)
          total += 1

          var changed: Boolean = false
          if (toplist.size < MAX_TOPLIST_SIZE) {
            toplist.append((thing, n))
            changed = true
          } else if (toplist.last._2 < n) {
            // Object enters toplist or is already there. Try to find it to...
            toplist.zipWithIndex.find({ case ((obj, _), _) => obj == o}) match {
              case Some((_, idx)) => toplist(idx) = (o, n) // update or...
              case None => toplist(MAX_TOPLIST_SIZE - 1) = (o, n) // if not found replace the last
            }
            changed = true
          }

          if (changed) {
            // New toplist, resort and distribute
            Sorting.stableSort(toplist, (A: (Any, Int), B: (Any, Int)) => (A, B) match {
              case ((_, a), (_, b)) if a < b => true
              case _ => false
            })

            target ! ??? //TODO: Figure out how to best forward this
          }
        }
        case None => log.debug(s"Message ${o.uid} doesn't have field $fieldOfInterest")
      }
  }
}
