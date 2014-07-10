package backend.flowNetwork

import akka.actor.Props
import backend.NextFlowUID
import backend.flowTypes.{NumberObject, FlowObject}

case class SetFilter(filter: String)
case object GetFilter
case object GetDropped

object FlowFilter {
  def prop(): Props = Props(new FlowFilter)
}

class FlowFilter extends TargetableFlow with FlowFieldOfInterest {
  var filter: String = ""
  var dropped: Int = 0

  def common: Receive = handleFieldOfInterest orElse {
    case SetFilter(f) =>
      log.info(s"Updating filter to '$f' for ${sender()}")
      filter = f
    case GetFilter => sender() ! filter
    case GetDropped => sender() ! dropped
  }

  override def passive: Receive = common
  override def active: Receive = common orElse {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(value) =>
          if (value matches filter) target ! o
          else dropped += 1
        case None => log.debug(s"Message ${o.uid} doesn't have a String convertible field $fieldOfInterest")
      }
  }
}
