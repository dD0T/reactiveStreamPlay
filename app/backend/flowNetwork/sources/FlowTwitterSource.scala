package backend.flowNetwork.sources

import akka.actor.Props
import backend.NextFlowUID
import backend.flowNetwork.{FlowNode, TargetableFlow}
import backend.flowTypes.TwitterMessage
import play.api.Play
import twitter4j._

object FlowTwitterSource {
  var nodeType = "TwitterSource"
  def props(id:Long, name: String,  x: Int, y: Int): Props = Props(new FlowTwitterSource(id, name, x, y))
}

case class Tweet(message: String)

class FlowTwitterSource(id: Long, name: String,  x: Int, y: Int)
  extends FlowNode(id, name, FlowTwitterSource.nodeType, x, y, 1, 0) with TargetableFlow {

  val twitterConfig = new conf.ConfigurationBuilder()
    .setOAuthConsumerKey(Play.current.configuration.getString("twitter.consumerkey").get)
    .setOAuthConsumerSecret(Play.current.configuration.getString("twitter.consumersecret").get)
    .setOAuthAccessToken(Play.current.configuration.getString("twitter.accesstoken").get)
    .setOAuthAccessTokenSecret(Play.current.configuration.getString("twitter.tokensecret").get)
    .setDebugEnabled(true)
    .build

  val statusListener = new StatusListener {
    override def onStatus(status: Status) =
      self ! Tweet(status.getText) // Pass message to us via akka to escape possible thread nastiness

    override def onDeletionNotice(statusDeletionNotice: StatusDeletionNotice) = log.warning("Deleted")

    override def onTrackLimitationNotice(numberOfLimitedStatuses: Int) = log.warning("Limit")

    override def onException(ex: Exception) = log.error(ex, "Twitter exception")

    override def onScrubGeo(arg0: Long, arg1: Long) = log.warning("Scrub geo")

    override def onStallWarning(warning: StallWarning) = log.warning("Stall warning")

  }

  val connectionCycleListener = new ConnectionLifeCycleListener {
    /**
     * called after connection was established
     */
    override def onConnect = log.info("Connected")

    /**
     * called after connection was disconnected
     */
    def onDisconnect = log.info("Disconnected")

    /**
     * called before thread gets cleaned up
     */
    def onCleanUp = log.info("Thread cleanup imminent")
  }

  val twitterStream = new TwitterStreamFactory(twitterConfig).getInstance
  twitterStream.addConnectionLifeCycleListener(connectionCycleListener)
  twitterStream.addListener(statusListener)
  twitterStream.sample

  var received = 0

  override def postStop() = {
    twitterStream.cleanUp
    twitterStream.shutdown
    super.postStop()
  }

  addConfigMapGetters(() => Map(
    "active" -> "1",
    "received" -> received.toString,
    "display" -> "received"
  ))

  override def passive: Receive = {
    case Tweet(_) =>
      received += 1 // Discard
      configUpdated()
  }
  override def active: Receive = {
    case Tweet(message) =>
      received += 1
      target ! TwitterMessage(NextFlowUID(), message)
      configUpdated()
  }
}
