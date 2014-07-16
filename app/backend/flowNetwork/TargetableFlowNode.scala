package backend.flowNetwork

import akka.actor.{ActorRef, ActorLogging, Actor}

case class SetTarget(target: ActorRef)
case class AddTarget(target: ActorRef)
case class RemoveTarget(target: ActorRef)
case object ClearTarget

trait TargetableFlow extends FlowNode {
  var target: ActorRef = null

  def active: Receive
  def passive: Receive = PartialFunction.empty

  addConfigMapGetters(() => Map(
    "active" -> (if (target != null) "true" else "false"),
    "target" -> (if (target != null) target.path.toString() else "None")
  ))

  private def setInitialTarget(t: ActorRef) = {
    log.info(s"Set target to $t . Becoming active.")
    target = t
    configUpdated()
    context.become(activeTargetable)
  }

  private def passiveTargetable: Receive = super.receive orElse passive orElse {
    case SetTarget(t) => setInitialTarget(t)
    case AddTarget(t) => setInitialTarget(t)
  }

  private def clearTarget = {
    log.info(s"Clearing target, switching to passive mode.")
    target = null
    configUpdated()
    context.become(passiveTargetable)
  }

  private def activeTargetable: Receive = super.receive orElse active orElse {
    case SetTarget(t) =>
      log.info(s"Retargeting to $t")
      target = t

    case AddTarget(t) =>
      log.warning(s"Asked to add target $t by ${sender()} while still targeted at $target")

    case ClearTarget => clearTarget
    case RemoveTarget(t) if t == target => clearTarget
  }

  final override def receive = passiveTargetable
}
