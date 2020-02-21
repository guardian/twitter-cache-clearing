package com.gu.socialCacheClearing

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.danielasfregola.twitter4s.{RefreshResponse, TwitterRefreshClient}
import com.gu.contentapi.client.GuardianContentClient
import com.gu.socialCacheClearing.Monad.{MonadFuture, MonadIdentity}
import com.gu.socialCacheClearing.Twitter.Production.{accessToken, consumerToken}

import scala.concurrent.ExecutionContext.global
import com.gu.crier.model.event.v1.Event
import com.gu.socialCacheClearing

import scala.concurrent.Future


abstract class Lambda[F[_], Event, CapiEvent](
  implicit f: Monad[F],
  k: Kinesis[Event, CapiEvent],
  c: Capi[F, CapiEvent],
  o: Ophan[F],
  t: Twitter[F],
  l: Logger[F, CapiEvent]
) {
  def program(event: Event): F[Set[RefreshResponse]] = {

    val capiEvents = capiEventsFromKinesisEvent(event)

    sealed trait TwitterServiceResponse
    case object TwitterServiceNotCalled extends TwitterServiceResponse
    case class TwitterServiceRefreshResponse(response: Set[RefreshResponse])

    def refreshResponseForRecentIds(
      recentUpdateIds: ::[Id]): F[Set[RefreshResponse]] = {
      for {
        urls <- sharedUrlsForContentIds(recentUpdateIds).map(_.toList)
        _ <- l.logSharedUrls(urls)
        res <- urls match {
          case Nil => f.pure(Set.empty[RefreshResponse])
          case urls: ::[String] => refreshRequestForSharedUrls(urls.toSet)
        }
      } yield res
    }

    def refreshResponseForIds(ids: ::[Id])(
      implicit f: Monad[F]): F[Set[RefreshResponse]] = {
      for {
        recentUpdates <- recentUpdates(ids)
        _ <- l.logRecentlyUpdatedIds(recentUpdates)
        res <- recentUpdates match {
          case Nil => f.pure(Set.empty[RefreshResponse])
          case someIds: ::[Id] => refreshResponseForRecentIds(someIds)
        }
      } yield res
    }

    // could we use a different effect which means we don't need to match on Nil each time?
    // cats? ZIO?
    l.logCapiEvents(capiEvents).flatMap { _ =>
    contentIdsForCapiEvents(capiEvents) match {
      case Nil => f.pure(Set.empty)
      case contentIds: ::[Id] => refreshResponseForIds(contentIds)
    }
  }
  }

  def capiEventsFromKinesisEvent(event: Event)(
      implicit kinesis: Kinesis[Event, CapiEvent]): List[CapiEvent] = {
    kinesis.capiEvents(event)
  }

  //TODO: we're defining type F[_] but we don't use it
  def contentIdsForCapiEvents(events: List[CapiEvent])(
      implicit capi: Capi[F, CapiEvent]): List[Id] = {
    capi.eventsToIds(events)
  }

  def recentUpdates(ids: ::[Id])(
      implicit capi: Capi[F, _]): F[List[Id]] = {
    capi.idsToRecentlyUpdatedIds(ids)
  }

  def sharedUrlsForContentIds(contentIds: ::[Id])(
      implicit ophan: Ophan[F]): F[SharedURLs] = {
    ophan.idsToSharedUrls(contentIds)
  }

  def refreshRequestForSharedUrls(sharedUrls: SharedURLs)(
      implicit twitter: Twitter[F]): F[Set[RefreshResponse]] = {
    for {
      responses <- twitter.refreshSharedUrls(sharedUrls)
      _ <- l.logRefreshResponses(responses)
    } yield {
      responses
    }
  }

}

class ProductionLambda extends Lambda[Future, KinesisEvent, Event]()(
  new MonadFuture(global),
  Kinesis.Production,
  new Capi.Production(ProductionLambda.capiClient)(global),
  new Ophan.Production,
  new Twitter.Production(ProductionLambda.twitterClient),
  new socialCacheClearing.Logger.ProductionLogger()(global)
) with RequestHandler[KinesisEvent, Unit] {

  override def handleRequest(event: KinesisEvent, context: Context): Unit =
    program(event)
}

object ProductionLambda {
  val capiClient = new GuardianContentClient(Credentials.getCredential("capi-api-key"))
  val twitterClient = new TwitterRefreshClient(consumerToken, accessToken)
}


class TestLambda extends Lambda[Identity, List[String], String]()(
  new MonadIdentity,
  new Kinesis[List[String], String] {
    def capiEvents(event: List[String]): List[String] = event
  },
  new Capi[Identity, String] {
    def eventToId: PartialFunction[String, String] = {
      case e => e
    }

    def idsToRecentlyUpdatedIds(ids: ::[String]): Identity[List[String]] = {
      ids.filter(_.startsWith("recent"))
    }
  },
  new Ophan[Identity] {
    def idsToSharedUrls(ids: ::[String]): Identity[Set[String]] = {
      val base = "www.theguardian.com/"
      ids.map(id => s"${base + id}?cmp=x").toSet
    }
  },
  new Twitter[Identity] {
    def refreshSharedUrls(urls: SharedURLs): Identity[Set[RefreshResponse]] =
      urls.map(_ => RefreshResponse("200"))
  },
  new Logger[Identity, String] {
    def logCapiEvents(events: List[String]): Identity[Unit] = println(s"capiEvents: $events")
    def logRecentlyUpdatedIds(recentlyUpdatedIds: List[String]) = println(s"recentlyUpdatedIds: $recentlyUpdatedIds")
    def logSharedUrls(sharedURLs: List[String]) = println(s"sharedUrls: $sharedURLs")
    def logRefreshResponses(responses: Set[RefreshResponse])= println(s"twitterRefreshResponses: $responses")
  }
) {
  def run(event: List[String]): Identity[Set[RefreshResponse]] = program(event)
}
