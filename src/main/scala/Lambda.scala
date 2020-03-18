package com.gu.socialCacheClearing

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.danielasfregola.twitter4s.{RefreshResponse, TwitterRefreshClient}
import com.gu.contentapi.client.GuardianContentClient
import com.gu.socialCacheClearing.Monad.{MonadFException, MonadFutureException}
import com.gu.socialCacheClearing.Twitter.Production.{accessToken, consumerToken}

import scala.concurrent.ExecutionContext.global
import com.gu.crier.model.event.v1.{Event, EventType, ItemType}
import com.gu.socialCacheClearing

import scala.concurrent.{Await, Future, duration}

// test change for git hook test
abstract class Lambda[F[_], Event, CapiEvent](
    implicit f: MonadFException[Throwable, F],
    k: Kinesis[Event, CapiEvent],
    c: Capi[F, CapiEvent],
    o: Ophan[F],
    t: Twitter[F],
    l: Logger[F, CapiEvent]
) {
  sealed trait TwitterServiceResponse
  case object TwitterServiceNotCalled extends TwitterServiceResponse
  case class TwitterServiceRefreshResponse(response: Set[RefreshResponse])

  def program(event: Event): F[Set[RefreshResponse]] = {

    val capiEvents = capiEventsFromKinesisEvent(event)

    for {
      _ <- l.logCapiEvents(capiEvents)
      contentIds <- f.pure(contentIdsForCapiEvents(capiEvents))
      _ <- l.logContentIdsForCapiEvents(contentIds)
      res <- contentIds match {
        case Nil =>
          f.raise[Set[RefreshResponse]](new Exception(
            "no content Ids"))
        case contentIds: ::[Id] =>
          for {
            recentUpdates <- recentUpdates(contentIds)
            _ <- l.logRecentlyUpdatedIds(recentUpdates)
            res <- recentUpdates match {
              case Nil =>
                f.raise[Set[RefreshResponse]](new Exception(
                  "no recent updates for content Ids"))
              case someIds: ::[Id] =>
                for {
                  urls <- sharedUrlsForContentIds(someIds).map(_.toList)
                  _ <- l.logSharedUrls(urls)
                  res <- urls match {
                    case Nil =>
                      f.raise[Set[RefreshResponse]](
                        new Exception("no shared urls for content Ids"))
                    case urls: ::[String] =>
                      for {
                        responses <- t.refreshSharedUrls(urls.toSet)
                        _ <- l.logRefreshResponses(responses)
                      } yield responses
                  }
                } yield res
            }
          } yield res
      }
    } yield res
  }

  def capiEventsFromKinesisEvent(event: Event): List[CapiEvent] = {
    k.capiEvents(event)
  }

  //TODO: we're defining type F[_] but we don't use it
  def contentIdsForCapiEvents(events: List[CapiEvent]): List[Id] = {
    c.eventsToIds(events)
  }

  def recentUpdates(ids: ::[Id]): F[List[Id]] = {
    c.idsToRecentlyUpdatedIds(ids)
  }

  def sharedUrlsForContentIds(contentIds: ::[Id]): F[SharedURLs] = {
    o.idsToSharedUrls(contentIds)
  }

}


object StagingLambda extends Lambda[Future, List[String], Event]()(
  new MonadFutureException(global),
  new Kinesis[List[String], Event] {
    def capiEvents(event: List[String]): List[Event] = List(Event("id", EventType.RetrievableUpdate, ItemType.Content, System.currentTimeMillis(), None))
  },
  new Capi.Production(ProductionLambda.capiClient)(global),
  new Ophan.Production,
  new Twitter.Production(ProductionLambda.twitterClient),
  new socialCacheClearing.Logger.ProductionLogger()(global)
) with App {

  val res = Await.result(program(List.empty), duration.Duration.Inf)

  println(s"res: $res")
}

class ProductionLambda
    extends Lambda[Future, KinesisEvent, Event]()(
      new MonadFutureException(global),
      Kinesis.Production,
      new Capi.Production(ProductionLambda.capiClient)(global),
      new Ophan.Production,
      new Twitter.Production(ProductionLambda.twitterClient),
      new socialCacheClearing.Logger.ProductionLogger()(global)
    )
    with RequestHandler[KinesisEvent, Unit] {

  override def handleRequest(event: KinesisEvent, context: Context): Unit =
  {
    val res = Await.result(program(event), duration.Duration(context.getRemainingTimeInMillis, duration.MILLISECONDS))
    println(res)
  }

}

object ProductionLambda {
  val capiClient = new GuardianContentClient(
    Credentials.getCredential("capi-api-key"))
  val twitterClient = new TwitterRefreshClient(consumerToken, accessToken)
}
