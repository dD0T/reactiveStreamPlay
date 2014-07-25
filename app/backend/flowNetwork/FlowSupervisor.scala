package backend.flowNetwork

import akka.actor._
import backend.NextFlowUID
import backend.flowNetwork.sinks.{FlowAccumulator, FlowFrequency, FlowTrace, FlowCounter}
import backend.flowNetwork.sources.{FlowTwitterSource, FlowIpsumSource, FlowNumberSource}
import backend.flowNetwork.transformations._
import play.api.libs.iteratee.Concurrent
import play.api.libs.json.JsValue

/** Registers an observer for node and connection configuration updates */
case class Register(observer: ActorRef)
/** Unregisters an observer for node and connection configuration updates */
case class Unregister(observer: ActorRef)

/** Triggers a complete re-discovery of the whole network configuration */
case object DetectConfiguration

/** Requests Some[ActorRef] of the connection between given source and target from Supervisor */
case class LookupConnection(sourceId: Long, targetId: Long)
/** Requests Some[ActorRef] of the node with the given id */
case class LookupObj(id: Long)
/** Requests Some[Long] ID of the node with the given ActorRef */
case class LookupId(obj: ActorRef)

/** Requests a list of possible flow objects */
case object GetFlowObjectTypes
/** Requests a set of all object IDs */
case object GetFlowObjects

/** Requests the creation of a flow object.
  * Answers with Some((id, ActorRef)) on success. None on failure.
  *
  * @param what NodeType to create
  * @param x X-Position in visualisation
  * @param y Y-Position in visualisation
  */
case class CreateFlowObject(what: String, x: Int, y: Int)
/** Requests deletion of the flow object with the given ID
  * Answers Some(id) on success, None on failure
  */
case class DeleteFlowObject(id: Long)

/** Requests a list of (sourceId, targetId) tuples */
case object GetConnections

/** Requests the connection between source and target.
  * Answers with Some(((sourceId, targetId), connection:ActorRef)) on success. None on failure.
  *
  * @param sourceId Source ID
  * @param targetId Target ID
  * @param attributes Connection attributes freely settable by the client (e.g. for connector positioning)
  */
case class Connect(sourceId: Long, targetId: Long, attributes: Map[String, String])

/** Requests disconnect of sourceId and targetId
  * Answers with Some((sourceId, targetId)) on success. None on failure.
  */
case class Disconnect(sourceId: Long, targetId: Long)

/** Used by FlowSupervisor to request shutdown from its child actors */
case object Shutdown
/** Used by child actors to aknowledge supervisor shutdown request */
case object Stopping

/** Requests a Enumerator[JsValue] subscribed to all network configuration updates.
  * Receives the full current configuration of the network (nodes then connections)
  * before streaming of configuration updates starts.
  */
case object RequestEnumerator

/** Used for representation and update of the (key -> value) configuration in a Node/Connection */
case class Configuration(config: Map[String, String])
/** Requests configuration from a FlowNode/Connection */
case object GetConfiguration

object FlowSupervisor {
  def props(): Props = Props(new FlowSupervisor)
}

/** The FlowSupervisor handles central supervision and control tasks for the Flow network.
 *  It is the central instance to talk to for network reconfiguration or
 *  observation.
 */
class FlowSupervisor extends Actor with ActorLogging {

  val ordinaryFlowObjects = Map[String, (Long, String, Int, Int) => Props](
    FlowNumberSource.nodeType -> FlowNumberSource.props,
    FlowCrossbar.nodeType -> FlowCrossbar.props,
    FlowFilter.nodeType -> FlowFilter.props,
    FlowTokenizer.nodeType -> FlowTokenizer.props,
    FlowFrequency.nodeType -> FlowFrequency.props,
    FlowSentiment.nodeType -> FlowSentiment.props,
    FlowAccumulator.nodeType -> FlowAccumulator.props,
    FlowIpsumSource.nodeType -> FlowIpsumSource.props,
    FlowCounter.nodeType -> FlowCounter.props,
    FlowTrace.nodeType -> FlowTrace.props,
    FlowTwitterSource.nodeType -> FlowTwitterSource.props,
    FlowMultiplier.nodeType -> FlowMultiplier.props,
    FlowStopwordFilter.nodeType -> FlowStopwordFilter.props
  )

  /** Helper object which name sequences when called repeatedly.
    * e.g. generates Name1, Name2, ...  for newActorName("Name")
    */
  object newActorName {
    var objectCounts = scala.collection.mutable.Map[String, Int]().withDefaultValue(0)
    def apply(name: String) = {
      objectCounts(name) += 1
      s"$name${objectCounts(name)}"
    }
  }

  var flowIdToObject = scala.collection.mutable.Map.empty[Long, ActorRef]
  var flowObjectToId = scala.collection.mutable.Map.empty[ActorRef, Long]

  var connectionObjToIds = scala.collection.mutable.Map.empty[ActorRef, (Long, Long)]
  var connectionIdsToObj = scala.collection.mutable.Map.empty[(Long, Long), ActorRef]

  var currentNodeConfigurations = scala.collection.mutable.Map.empty[Long, Configuration]
  var currentConnectionConfigurations = scala.collection.mutable.Map.empty[(Long, Long), Configuration]

  var translators =  scala.collection.mutable.Map.empty[Long, ActorRef]

  var observers = scala.collection.mutable.Set.empty[ActorRef]

  /** Returns all connections related to the given node id */
  private def connectionsFor(id: Long): scala.collection.mutable.Map[(Long, Long), ActorRef] =
    connectionIdsToObj.filter { case ((sourceId, targetId),_) => sourceId == id || targetId == id }

  /** Broadcasts the given message to all registered observers */
  private def notifyObservers[T](message: T) = observers map { _ ! message }

  /** Removes an existing connection between two nodes.
    *
    * @param sourceId Source node ID
    * @param targetId Target node ID
    * @return true on success. False on failure.
    */
  private def disconnect(sourceId: Long, targetId: Long): Boolean = {
    connectionIdsToObj.remove((sourceId, targetId)) match {
      case Some(connection) =>
        log.info(s"Disconnecting $sourceId and $targetId")

        flowIdToObject.get(sourceId) match {
          case Some(source) => source ! RemoveTarget(connection)
          case None => // Fine, already gone
        }

        currentConnectionConfigurations.remove((sourceId, targetId))
        connectionIdsToObj.remove((sourceId, targetId))

        notifyObservers((sourceId, targetId), None)
        connection ! Shutdown
        true

      case _ =>
        log.warning(s"Asked to disconnect $sourceId from $targetId but have no connection")
        false
    }
  }

  def receive = {
    case GetFlowObjectTypes => sender() ! ordinaryFlowObjects.keys.toList

    case RequestEnumerator =>
      import play.api.libs.concurrent.Execution.Implicits.defaultContext

      val uid = NextFlowUID()
      log.info(s"Translator reservation $uid")

      val shutdownTranslator = () => {
        translators.get(uid) match {
          case Some(translator) =>
            log.info(s"Shutting down translator $uid")
            translators.remove(uid)
            self ! Unregister(translator)
            translator ! Shutdown
          case None => log.info(s"Translator $uid already gone")
        }
      }

      val enumerator = Concurrent.unicast[JsValue](
        onStart = (channel) => {
          // Seems like someone actually
          log.info(s"Starting new translator for client session $uid")
          val translator = context.actorOf(MessageTranslator.props(channel), name = s"messageTranslator$uid")
          translators += uid -> translator
          self ! Register(translator)
          // Push current state
          currentNodeConfigurations map { case (k,v) => translator ! (k, v) }
          currentConnectionConfigurations map { case (k,v) => translator ! (k,v) }
        },
        onComplete = { shutdownTranslator() },
        onError = (_,_) => shutdownTranslator()
      ).onDoneEnumerating(
          callback = shutdownTranslator
      )

      sender() ! enumerator

    case Register(observer) =>
      log.info(s"$observer registered for updates")
      observers += observer

    case Unregister(observer) =>
      log.info(s"$observer removed from updates")

    case DetectConfiguration =>
      log.info(s"${sender()} triggered configuration update")
      // Notify about current configuration state
      currentNodeConfigurations map { case (k,v) => notifyObservers(k, v) }
      currentConnectionConfigurations map { case (k,v) => notifyObservers(k,v) }

    case CreateFlowObject(objectType, x, y) =>
      ordinaryFlowObjects.get(objectType) match {
        case Some(_) =>
          log.info(s"Creating new $objectType for ${
            sender()
          }")
          val name = newActorName(objectType)
          val id = NextFlowUID()
          val obj = context.actorOf(ordinaryFlowObjects(objectType)(id, name, x, y), name = name)
          flowIdToObject += id -> obj
          flowObjectToId += obj -> id

          obj ! GetConfiguration // Pull configuration
          sender() ! Some((id, obj))

        case None =>
          log.warning(s"Asked to create unknown flow object type $objectType")
          None
      }

    case DeleteFlowObject(id: Long) =>
      flowIdToObject.get(id) match {
        case Some(obj) =>
          log.info(s"Deleting node $id")

          // First disconnect
          connectionsFor(id) map { case ((sourceId, targetId), _) => disconnect(sourceId, targetId)}
          // Then delete
          obj ! Shutdown

          flowIdToObject.remove(id)
          flowObjectToId.remove(obj)

          sender() ! Some(id) // Ack delete of ID
          currentNodeConfigurations.remove(id)
          notifyObservers(id, None)

        case None =>
          log.warning(s"Asked to delete unknown object $id")
          sender() ! None
      }

    case Configuration(data) =>
      flowObjectToId.get(sender()) match {
        case Some(id) =>
          currentNodeConfigurations(id) = Configuration(data)
          notifyObservers(id, Configuration(data))
        case None =>
          connectionObjToIds.get(sender()) match {
            case Some((sourceId, targetId)) =>
              currentConnectionConfigurations((sourceId, targetId)) = Configuration(data)
              notifyObservers((sourceId, targetId), Configuration(data))
            case None => log.error(s"Received configuration update for untracked actor ${sender()}")
          }
      }

    case (id: Long, Configuration(data)) =>
      // Forward config to addressed actor
      flowIdToObject.get(id) match {
        case Some(obj) =>
          obj ! Configuration(data)
          sender() ! true

        case None =>
          log.error(s"Asked to forward configuration for unknown id $id")
          sender ! false
      }

    case LookupConnection(sourceId, targetId) =>
      connectionIdsToObj.get((sourceId, targetId)) match {
        case Some(connection) => sender() ! Some(connection)
        case None => sender() ! None
      }

    case LookupObj(id) =>
      flowIdToObject.get(id) match {
        case Some(obj) => sender() ! Some(obj)
        case None => sender() ! None
      }

    case LookupId(obj) =>
      flowObjectToId.get(obj) match {
        case Some(id) => sender() ! Some(id)
        case None => sender() ! None
      }

    case GetConnections =>
      sender() ! connectionIdsToObj.keySet.toSet

    case GetFlowObjects =>
      sender() ! flowIdToObject.keySet.toSet

    case Connect(sourceId, targetId, attributes) =>
      (flowIdToObject.get(sourceId), flowIdToObject.get(targetId)) match {
        case (Some(source), Some(target)) if !connectionIdsToObj.contains { (sourceId, targetId) } =>

          log.info(s"Creating new connection from $source to $target")
          val connection = context.actorOf(
            FlowConnection.props(source, sourceId, target, targetId, attributes),
            name = newActorName("FlowConnection"))

          connectionObjToIds += connection ->(sourceId, targetId)
          connectionIdsToObj += (sourceId, targetId) -> connection

          source ! AddTarget(connection)
          sender() ! Some(((sourceId, targetId), connection))
          connection ! GetConfiguration

        case _ =>
          sender ! None
          log.warning(s"Asked to connect $sourceId with $targetId of which are invalid or already connected")
      }

    case Disconnect(sourceId, targetId) =>
      if (disconnect(sourceId, targetId)) {
        sender() ! Some((sourceId, targetId))
      } else {
        sender() ! None
      }
  }
}
