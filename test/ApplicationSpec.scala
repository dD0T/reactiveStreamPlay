import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application" should {

    "send 404 on a bad request" in new WithApplication{
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "render the index page" in new WithApplication{
      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Hi there")
    }

    "render the flow page" in new WithApplication{
      val home = route(FakeRequest(GET, "/flow")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
    }
  }
/*TODO: Figure out how to run this (see http://stackoverflow.com/questions/18037940/play-fakerequest-with-specs2-remembers-request-across-tests)
  "ApplicationNode" should {
    "be able to create node on request" in new WithApplication {
      var body = Json.obj(
        "nodeType" -> "FlowSource",
        "x" -> "0",
        "y" -> "0"
      )

      var response = route(
        FakeRequest(POST, "/node")
          .withJsonBody(body)
      ).get

      println(response.toString)
      contentAsJson(response).validate[Map[String,String]].asOpt must beSome
    }

  }
  */
}
