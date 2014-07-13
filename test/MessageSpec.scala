import backend.flowTypes.{WordObject, TwitterMessage, Sentiment, FlowObject}
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

class FlowTest extends FlowObject {
  /** Retrieves the content of the given field. None if invalid field */
  override def content(field: String): Option[Any] = field match {
    case "foo" | "default" => Some(foo)
    case "bar" => Some(bar)
    case "buz" => Some(buz)
    case "biz" => Some(biz)
    case _ => None
  }

  override def fields(): List[String] = List("foo", "bar", "buz", "biz", "default")

  val foo: Int = 5
  val bar: String = "20"
  val buz: Double = 4.2
  val biz: String = "biz"

  override val uid: Long = 0
  override val originUid: Long = 1
}

@RunWith(classOf[JUnitRunner])
class MessageSpec extends Specification {
  "FlowObject test class FlowTest" should {
    val o = new FlowTest

    "have proper conversions for int" in {
      o.contentAsInt() must beSome(5)
      o.contentAsDouble() must beSome(5.0)
      o.contentAsString() must beSome("5")
    }

    "have proper conversions for string" in {
      o.contentAsInt("bar") must beSome(20)
      o.contentAsDouble("bar") must beSome(20.0)
      o.contentAsString("bar") must beSome("20")
    }

    "have proper conversions for double" in {
      o.contentAsInt("buz") must beSome(4)
      o.contentAsDouble("buz") must beSome(4.2)
      o.contentAsString("buz") must beSome("4.2")
    }

    "have failure for invalid conversions" in {
      o.contentAsInt("biz") must beNone
      o.contentAsDouble("biz") must beNone
      o.contentAsString("doesnotexist") must beNone
    }
  }
  "TwitterMessage" should {
    val msg = TwitterMessage(0, "Bernd")

    "have 'message' field" in {
      msg.fields() must contain("message")
      msg.contentAsString("message") must beSome(msg.message)
    }

    "fail arbitrary conversions" in {
      msg.contentAsInt("message") must beNone
    }

    "not have random field" in {
      msg.contentAsString("doesNotExist") must beNone
    }

    "default to 'message'" in {
      msg.contentAsString() must beSome(msg.message)
      msg.fields() must contain("default")
    }

  }

  "Word" should {
    val msg = WordObject(0, 1, "Foo")

    "have 'word' field" in {
      msg.fields() must contain("word")
      msg.contentAsString("word") must beSome(msg.word)
    }

    "have predecessor" in {
      msg.originUid must beEqualTo(1)
      WordObject(10, msg, "Blub").originUid must beEqualTo(msg.uid)
    }
  }

  "Sentiment" should {
    val msg = Sentiment(0, 1, 5,4)

    "have 'sentiment' default field" in {
      msg.fields() must contain("sentiment")
      msg.content() must beEqualTo(msg.content("sentiment"))
      msg.contentAsDouble() must beSome(1)

    }

    "have predecessor" in {
      msg.originUid must beEqualTo(1)
      Sentiment(10, msg, 23, 22).originUid must beEqualTo(msg.uid)
    }
  }
}
