package backend.flowNetwork.transformations

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{FlowNode, FlowFieldOfInterest, TargetableFlow}
import backend.flowTypes.{Sentiment, FlowObject}
import play.api.Play

//TODO: Should probably utilize http://sentiwordnet.isti.cnr.it/ instead of assuming equally scored good/badwords

object FlowSentiment {
  var nodeType = "Sentiment"

  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowSentiment(id, name, x, y))
}

class FlowSentiment(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowSentiment.nodeType, x, y, 1, 1) with TargetableFlow with FlowFieldOfInterest {

  val positiveWords = Tools.loadWordsetFromFile(Play.current.configuration.getString("sentiment.positive"))
  val negativeWords = Tools.loadWordsetFromFile(Play.current.configuration.getString("sentiment.negative"))

  var accumulatedPositive = 0
  var accumulatedNegative = 0

  addConfigMapGetters(() => Map(
    "positive" -> accumulatedPositive.toString,
    "negative" -> accumulatedNegative.toString,
    "sentiment" -> (accumulatedPositive - accumulatedNegative).toString,
    "#positive@list" -> positiveWords.size.toString,
    "#negative@list" -> negativeWords.size.toString,
    "display" -> "positive,negative,sentiment,field"
  ))

  override def active: Receive = {
    case o: FlowObject =>
      o.contentAsString(fieldOfInterest) match {
        case Some(content) =>
          // We don't tokenize ourselves because then what would be the point of the tokenizer ;)
          // Also actual real-world tokenizing is pretty complicated.
          val word = content.toLowerCase

          var positive = 0
          var negative = 0

          if (positiveWords contains word) positive += 1
          if (negativeWords contains word) negative += 1

          accumulatedPositive += positive
          accumulatedNegative += negative

          target ! Sentiment(NextFlowUID(), o.uid, positive, negative)
          configUpdated()

        case None => // Not convertible to string (can this even happen?^^)
      }

  }
}
