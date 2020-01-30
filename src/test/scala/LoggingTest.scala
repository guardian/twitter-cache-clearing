import com.danielasfregola.twitter4s.RefreshResponse
import com.gu.socialCacheClearing.{Capi, Kinesis, Lambda, Monad, Ophan, SharedURLs, Transition, Twitter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class Logger(messages: List[String]) {
  def addMessage(message: Option[String]) = message match {
    case Some(msg) => copy(messages :+ msg)
    case None => this
  }
}

class TestLoggerLambda extends Lambda {

  class TransitionModad[Input] extends Monad[Transition[Input, ?]] {
    def pure[A](a: A): Transition[Input, A] = i => (i, a)

    def map[A, B](fa: Transition[Input, A])(f: A => B): Transition[Input, B] = {
      i =>
        fa(i) match {
          case (newI, r) => (newI, f(r))
        }
    }

    def flatMap[A, B](fa: Transition[Input, A])(
      f: A => Transition[Input, B]): Transition[Input, B] = { i =>
      fa(i) match {
        case (newI, ra) => f(ra)(newI)
      }
    }
  }

  implicit val transitionMonad = new TransitionModad[Logger]

  type Event = List[String]

  implicit val kinesis = new Kinesis[Event, String] {
    def capiEvents(event: Event): List[String] = event
  }

  implicit val capi = new Capi[Transition[Logger, ?], String] {
    def eventToId: PartialFunction[String, String] = {
      case e => e
    }

    def idsToRecentlyUpdatedIds(ids: ::[String]): Transition[Logger, List[String]] = {
      logger => (logger.addMessage(None), ids.filter(_.startsWith("recent")))
    }
  }

  implicit val ophan = new Ophan[Transition[Logger, ?]] {
    def idsToSharedUrls(ids: ::[String]): Transition[Logger, Set[String]] = {
      val base = "www.theguardian.com/"
      logger => (logger.addMessage(None), ids.map(id => s"${base + id}?cmp=x").toSet)
    }
  }

  implicit val twitter = new Twitter[Transition[Logger, ?]] {
    def refreshSharedUrls(urls: SharedURLs): Transition[Logger, Set[RefreshResponse]] = {
      val responses = urls.map(_ => RefreshResponse("200"))
      logger => (logger.addMessage(Some(s"sent requests")), urls.map(_ => RefreshResponse("200")))
    }
  }

  def run(event: Event) =  program(event)

}

class LoggingTest extends AnyFlatSpec with Matchers {

  it should "log sending request message for an event set containing recent updates" in {
    val events = List("recent1", "recent2")

    val lambda = new TestLoggerLambda

    val initialLogger = Logger(List.empty)

    lambda.run(events)(initialLogger)._1.messages should be (List("sent requests"))
  }
}