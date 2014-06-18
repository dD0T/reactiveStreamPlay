package backend.flowNetwork

import akka.actor.{ActorRef, ActorLogging, Actor}

case class SetTarget(target: ActorRef)
case object ClearTarget

trait TargetableFlow extends Actor with ActorLogging {
  var target: ActorRef = null

  def active: Receive
  def passive: Receive = PartialFunction.empty

  private def passiveTargetable: Receive = passive orElse {
    case SetTarget(t) =>
      log.info(s"Set target to $t . Becoming active.")
      target = t
      context.become(activeTargetable)
  }

  private def activeTargetable: Receive = active orElse {
    case SetTarget(t) =>
      log.info(s"Retargeting to $t")
      target = t

    case ClearTarget => {
      log.info(s"No target, switching to passive mode.")
      target = null
      context.become(passiveTargetable)
    }
  }

  def receive = passiveTargetable
}
