package backend.flowNetwork

/**
 * Extends a FlowNode with a configurable field of interest (FOI).
 *
 * If a node has a FOI this will be the field say a filter
 * operation relates to. By default this is the "default" field
 * which all FlowObjects expose. For a twitter message this could
 * for example be changed to "lang" to filter on a users language
 * instead of the "default" field which would refer to the message
 * of the tweet.
 */
trait FlowFieldOfInterest extends FlowNode {
  var fieldOfInterest: String = "default"

  addConfigSetters({
    case ("field", foi) =>
      log.info(s"Updating FOI to $foi")
      fieldOfInterest = foi
  })

  addConfigMapGetters(() => Map(
    "field" -> fieldOfInterest
  ))
}
