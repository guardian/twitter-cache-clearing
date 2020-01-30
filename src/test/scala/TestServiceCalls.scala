import com.danielasfregola.twitter4s.RefreshResponse
import com.gu.socialCacheClearing.{Capi, Kinesis, Lambda, Monad, Ophan, SharedURLs, Transition, Twitter}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

case class ServiceCalls(capi: Int, ophan: Int, twitter: Int) {
  def incrementCapi = copy(capi = capi+1)
  def incrementOphan = copy(ophan = ophan+1)
  def incrementTwitter = copy(twitter = twitter+1)
}

class TestTransitionLambda extends Lambda {

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

  implicit val transitionMonad = new TransitionModad[ServiceCalls]

  type Event = List[String]

  implicit val kinesis = new Kinesis[Event, String] {
    def capiEvents(event: Event): List[String] = event
  }

  implicit val capi = new Capi[Transition[ServiceCalls, ?], String] {
    def eventToId: PartialFunction[String, String] = {
      case e => e
    }

    def idsToRecentlyUpdatedIds(ids: ::[String]): Transition[ServiceCalls, List[String]] = {
      serviceCalls => (serviceCalls.incrementCapi, ids.filter(_.startsWith("recent")))
    }
  }

  implicit val ophan = new Ophan[Transition[ServiceCalls, ?]] {
    def idsToSharedUrls(ids: ::[String]): Transition[ServiceCalls, Set[String]] = {
      val base = "www.theguardian.com/"
      serviceCalls => (serviceCalls.incrementOphan, ids.map(id => s"${base + id}?cmp=x").toSet)
    }
  }

  implicit val twitter = new Twitter[Transition[ServiceCalls, ?]] {
    def refreshSharedUrls(urls: SharedURLs): Transition[ServiceCalls, Set[RefreshResponse]] =
      serviceCalls => (serviceCalls.incrementTwitter, urls.map(_ => RefreshResponse("200")))
  }

  def run(event: Event) =  program(event)

}


class ServiceCallsTest extends AnyFlatSpec with Matchers {

  it should "not call services beyond Capi if no content is recently updated" in {
    val events = List("content1", "content2")

    val lambda = new TestTransitionLambda

    val initialServiceCalls = ServiceCalls(0,0,0)

    lambda.run(events)(initialServiceCalls)._1 should be (ServiceCalls(capi= 1, ophan= 0,twitter= 0))
  }
}