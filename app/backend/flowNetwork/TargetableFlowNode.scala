package backend.flowNetwork

import akka.actor.ActorRef

case class SetTarget(target: ActorRef)
case class AddTarget(target: ActorRef)
case class RemoveTarget(target: ActorRef)
case object ClearTarget

/**
 * Extends FlowNode with the capability to be targeted at other FlowNodes.
 *
 * A TargetableFlow is passive until a SetTarget command
 * gives it a target. Then it becomes active. Instead of
 * the usual receive function classes extended with this
 * overwrite the corresponding active/passive handlers.
 *
 * In active state the local variable target is guaranteed
 * to be set to an ActorRef pointing to the target. In passive
 * state this variable will be null and should not be touched.
 *
 * The state of the node is published to the frontend
 * via two configuration properties "active" and "target"
 * indicating whether the node has a target and what actor
 * exactly is targeted.
 */
trait TargetableFlow extends FlowNode {
  var target: ActorRef = null

  /** User overridable behavior for the active state */
  def active: Receive
  /** User overridable behavior for the passive state */
  def passive: Receive = PartialFunction.empty

  addConfigMapGetters(() => Map(
    "active" -> (if (target != null) "1" else "0"),
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
