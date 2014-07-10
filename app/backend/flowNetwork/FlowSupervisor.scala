package backend.flowNetwork

import akka.actor._


case object GetFlowObjectTypes
case class CreateFlowObject(what: String)

case object GetConnections
case class Connect(source: ActorRef, target: ActorRef)
case class Disconnect(source: ActorRef, target: ActorRef)

object FlowSupervisor {
  def props(): Props = Props(new FlowSupervisor)
}

class FlowSupervisor extends Actor with ActorLogging {
  import akka.actor.OneForOneStrategy
  import akka.actor.SupervisorStrategy._
  import scala.concurrent.duration._

  val ordinaryFlowObjects = Map[String, ()=>Props](
    "FlowSource" -> (()=>FlowSource.props),
    "FlowCrossbar" -> (()=>FlowCrossbar.props),
    "FlowTokenizer" -> (()=>FlowTokenizer.props),
    "FlowFrequency" -> (()=>FlowFrequency.props),
    "FlowSentiment" -> (()=>FlowSentiment.props),
    "FlowAccumulator" -> (()=>FlowAccumulator.props)
  )

  object newActorName {
    var objectCounts = scala.collection.mutable.Map[String, Int]().withDefaultValue(0)
    def apply(name: String) = {
      objectCounts(name) += 1
      s"$name${objectCounts(name)}"
    }
  }

  var flowObjects = scala.collection.mutable.Set.empty[ActorRef]
  var connections = scala.collection.mutable.Map.empty[(ActorRef, ActorRef), ActorRef]

  def receive = {
    case GetFlowObjectTypes => sender() ! ordinaryFlowObjects.keys.toList

    case CreateFlowObject(objectType) if ordinaryFlowObjects contains objectType =>
      log.info(s"Creating new $objectType for ${sender()}")
      val obj = context.actorOf(ordinaryFlowObjects(objectType)(), name = newActorName(objectType))
      flowObjects += obj
      sender() ! obj

    case GetConnections =>
      sender() ! connections

    case Connect(source, target) =>
      log.info(s"Creating new connection from $source to $target")
      val connection = context.actorOf(FlowConnection.props(source, target), name = newActorName("FlowConnection"))
      connections += (source, target) -> connection
      source ! AddTarget(connection)
      sender() ! connection

    case Disconnect(source, target) =>
      connections.remove((source,target)) match {
        case Some(connection) =>
          log.info(s"Disconnecting $source and $target")
          source ! RemoveTarget(connection)
          connection ! Kill
        case _ => log.warning(s"Asked to disconnect $source from $target but have no connection")
      }

  }
}
