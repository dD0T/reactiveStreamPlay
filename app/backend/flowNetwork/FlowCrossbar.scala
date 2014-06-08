package backend.flowNetwork

import akka.actor.{ActorRef, Actor, Props}
import akka.event.Logging
import backend.flowTypes.FlowObject

case class MembershipUpdate(targets: Set[ActorRef])
case class AddTarget(target: ActorRef)
case class RemoveTarget(target: ActorRef)

case object GetTargets
case class Targets(targets: Set[ActorRef])

class FlowCrossbar extends Actor {
  var targets = Set[ActorRef]()
  val log = Logging(context.system, this)

  def receive = {
    case GetTargets =>
      sender() ! Targets(targets)

    case o: FlowObject =>
      log.debug(s"Repeating $o to ${targets.size} targets")
      targets.map(_ ! o)

    case AddTarget(t) =>
      log.info(s"New target $t on $this")
      context.system.eventStream.publish(MembershipUpdate(targets))
      targets = targets + t

    case RemoveTarget(t) =>
      if (targets contains t) {
        log.info(s"Removed target $t on $this")
        targets = targets - t
        context.system.eventStream.publish(MembershipUpdate(targets))
      }
  }
}
