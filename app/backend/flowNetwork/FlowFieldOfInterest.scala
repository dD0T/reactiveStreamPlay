package backend.flowNetwork

import akka.actor.{ActorLogging, Actor}

case class SetFieldOfInterest(fieldOfInterest: String)
case class FieldOfInterest(fieldOfInterest: String)
case object GetFieldOfInterest

trait FlowFieldOfInterest extends Actor with ActorLogging {
  var fieldOfInterest: String = "default"

  def handleSetFieldOfInterest: Receive = {
    case SetFieldOfInterest(foi) =>
      log.info(s"Updating FOI to $foi")
      fieldOfInterest = foi
  }

  def handleGetFieldOfInterest: Receive = {
    case GetFieldOfInterest =>
      sender() ! FieldOfInterest(fieldOfInterest)
  }

  def handleFieldOfInterest: Receive = handleGetFieldOfInterest orElse handleSetFieldOfInterest
}
