package backend.flowNetwork.transformations

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}
import backend.flowTypes.{FlowObject, WordObject}

object FlowTokenizer {
  var nodeType = "Tokenizer"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowTokenizer(id, name, x, y))
}

class FlowTokenizer(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowTokenizer.nodeType, x, y, 1 ,1) with TargetableFlow with FlowFieldOfInterest {

  var separators = Array[Char](' ','.',',','!','?')

  val configStringSeperator = " and "

  addConfigMapGetters(() => Map(
    "separators" -> separators.mkString,
    "display" -> "separators"
  ))

  addConfigSetters({
    case ("separators", sep) =>
      log.info(s"Updating separators to chars: $sep")
      separators = sep.toCharArray
  })

  override def active: Receive = {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(content) =>
          content.split(separators)
                 .filter(!_.isEmpty)
                 .foreach(target ! WordObject(NextFlowUID(), o, _))

        case None => log.debug(s"Message ${o.uid} doesn't have a String convertible field $fieldOfInterest")
      }
  }
}
