package com.gu.socialCacheClearing

import com.danielasfregola.twitter4s.RefreshResponse
import com.gu.socialCacheClearing.Monad.MonadTryException
import org.scalatest.TryValues
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import scala.util.Try

class TestLambda
  extends Lambda[Try, List[String], String]()(
    new MonadTryException,
    new Kinesis[List[String], String] {
      def capiEvents(event: List[String]): List[String] = event
    },
    new Capi[Try, String] {
      def eventToId: PartialFunction[String, String] = {
        case e => e
      }

      def idsToRecentlyUpdatedIds(ids: ::[String]): Try[List[String]] = {
        Try(ids.filter(_.startsWith("recent")))
      }
    },
    new Ophan[Try] {
      def idsToSharedUrls(ids: ::[String]): Try[Set[String]] = {
        val base = "www.theguardian.com/"
        Try(ids.map(id => s"${base + id}?cmp=x").toSet)
      }
    },
    new Twitter[Try] {
      def refreshSharedUrls(
        urls: SharedURLs): Try[Set[RefreshResponse]] =
        Try(urls.map(_ => RefreshResponse("200")))
    },
    new Logger[Try, String] {
      def logCapiEvents(events: List[String]): Try[Unit] =
        Try(println(s"capiEvents: $events")) //TODO: Log something less verbose - a count?
      def logContentIdsForCapiEvents(ids: List[String]) =
        Try(println(s"ids: $ids"))
      def logRecentlyUpdatedIds(recentlyUpdatedIds: List[String]) =
        Try(println(s"recentlyUpdatedIds: $recentlyUpdatedIds"))
      def logSharedUrls(sharedURLs: List[String]) =
        Try(println(s"sharedUrls: $sharedURLs"))
      def logRefreshResponses(responses: Set[RefreshResponse]) =
        Try(println(s"twitterRefreshResponses: $responses"))
    }
  ) {
  def run(event: List[String]): Try[Set[RefreshResponse]] = program(event)
}


class LambdaIntegrationTest extends AnyFlatSpec with Matchers {
  val lambda = new TestLambda

  it should "return a refresh response for an input event set containing recent updates" in {
    val inputEvent = List("recentEvent1")

    //TODO: how could I use contain?
    TryValues.convertTryToSuccessOrFailure(lambda.run(inputEvent)).success.value should be (Set(RefreshResponse("200")))
  }

  it should "throw an exception for an empty input event set" in {
    val inputEvent = List.empty[String]

    TryValues.convertTryToSuccessOrFailure(lambda.run(inputEvent)).failure.exception should have message "no content Ids"
  }

}
