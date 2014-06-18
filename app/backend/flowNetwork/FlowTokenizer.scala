package backend.flowNetwork

import akka.actor.{ActorLogging, ActorRef, Props, Actor}
import backend.flowTypes.{WordObject, FlowObject}
import akka.event.Logging
import backend.NextFlowUID

case class SetSeparator(separator: String)
case class Separator(separator: String)
case object GetSeparator

object FlowTokenizer {
  def props(): Props = Props(new FlowTokenizer)
}

class FlowTokenizer extends TargetableFlow with FlowFieldOfInterest {
  var separator: String = " "

  def common: Receive = handleFieldOfInterest orElse {
    case SetSeparator(sep) =>
      log.info(s"Updating separator to $sep")
      separator = sep
    case GetSeparator =>
      sender() ! Separator(separator)
  }

  override def passive: Receive = common
  override def active: Receive = common orElse {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(content) => content.split(separator).foreach(target ! WordObject(NextFlowUID(), o, _))
        case None => log.debug(s"Message ${o.uid} doesn't have a String convertible field $fieldOfInterest")
      }
  }
}
