package backend.flowNetwork

import akka.actor.{Props, ActorRef, ActorLogging, Actor}

/** Used for representation and update of the (key -> value) configuration in a FlowNode */
case class Configuration(config: Map[String, String])
/** Requests configuration from a FlowNode */
case object GetConfiguration

object FlowNode {
  def props(id: Long, name:String, x: Int, y: Int, outputs: Int, inputs: Int): Props =
    Props(new FlowNode(id, name, x, y, outputs, inputs))
}

/**
 * Flow nodes represent the source, sink and transformation elements of the pipeline.
 *
 * They must be given a user visible display name, a unique id as well as a x,y position
 * on-screen.
 *
 * Flow nodes implement basic generic state retrieval and update functionality. If sent a
 * GetConfiguration request all relevant state of the actor is sent back in a Configuration
 * object. Partial or full updates of this configuration can be applied by sending a
 * Configuration object containing said updates to this actor. Whether application failed
 * or not this actor will send a full update of his state to his parent.
 *
 * @param name Display name for this actor
 * @param id Unique numeric ID of this actor
 * @param x X coordinate on screen
 * @param y Y coordinate on screen
 * @param outputs Number of outputs on this element
 * @param inputs Number of inputs on this element
 */
class FlowNode(val id:Long, var name: String,
               var x: Int, var y: Int,
               val outputs: Int, val inputs: Int) extends Actor with ActorLogging {

  /** Given (key, value) set config item key to value.
    *
    * If value isn't compatible with the function or could not be
    * set for any other reason the function must throw and exception
    * to indicate failure.
    */
  type ConfigSetters = PartialFunction[(String, String), Unit]

  /** Returns a map containing key,value representations of the config */
  type ConfigMapGetter = () => Map[String, String]

  /** Chains a additional ConfigSetters function before the existing ones.
    *
    * @param setters Setters to prepend. See ConfigSetters.
    *
    * Usage:
    *   addConfigSetters({
    *     case ("foo", v) => bar = v
    *   )}
    */
  def addConfigSetters(setters: ConfigSetters) =
    configSetters = setters orElse configSetters

  /** Chains a additional ConfigMapGetter function before the existing ones.
    *
    * @param map Getters to prepend. See ConfigMapGetter
    *
    * Usage:
    *   addConfigMapGetters(() => Map(
    *     "foo" -> "bar"
    *   ))
    */
  def addConfigMapGetters(map: ConfigMapGetter) = {
    configMaps = configMaps :+ map
  }

  /** Sends configuration to parent */
  def configUpdated() = context.parent ! Configuration(config)

  /** Create full config map on demand */
  def config = {
    configMaps.foldLeft(Map[String, String]()) {
      (r, m) => r ++ m()
    }
  }

  /** Map from configuration key to value as string representation. */
  private var configMaps: List[ConfigMapGetter] = List(() => Map(
    "name" -> name,
    "actor" -> self.path.toString(),
    "id" -> id.toString(),
    "x" -> x.toString(),
    "y" -> y.toString(),
    "outputs" -> outputs.toString(),
    "inputs" -> inputs.toString()
  ))

  /** Called for each potential change of a configuration variable. */
  private var configSetters: ConfigSetters = {
    case ("name", v: String) => name = v
    case ("x", v: String) => x = v.toInt
    case ("y", v: String) => y = v.toInt
    case (k, _) => throw new Exception(s"Unknown variable $k")
  }

  def receive: Receive = {
    case GetConfiguration =>
      sender() ! Configuration(config)

    case Configuration(changes) =>
      changes foreach { case (k,v) =>
        try { configSetters((k,v)) }
        catch { case e: Exception => log.error(e, s"Couldn't set $k to $v") }
      }
      // Send configuration update to system, if client was opportunistic this will
      // reset it in case we failed to set something
      configUpdated()
  }
}
