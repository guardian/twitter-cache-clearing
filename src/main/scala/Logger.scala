package com.gu.socialCacheClearing

import com.danielasfregola.twitter4s.RefreshResponse

import scala.concurrent.{ExecutionContext, Future}
import com.gu.crier.model.event.v1.Event

trait Logger[F[_], CapiEvent] {
  def logCapiEvents(events: List[CapiEvent]): F[Unit]
  def logContentIdsForCapiEvents(ids: List[Id]): F[Unit]
  def logRecentlyUpdatedIds(recentlyUpdatedIds: List[Id]): F[Unit]
  def logSharedUrls(sharedURLs: List[String]): F[Unit]
  def logRefreshResponses(responses: Set[RefreshResponse]): F[Unit]
}

object Logger {
  class ProductionLogger()(implicit ec: ExecutionContext) extends Logger[Future, Event] {

    def logCapiEvents(events: List[Event]) = Future {
      println(s"capiEvents: $events")
    }

    def logContentIdsForCapiEvents(ids: List[Id]) = Future {
      println(s"contentIdsForCapiEvents: $ids")
    }

    def logRecentlyUpdatedIds(recentlyUpdatedIds: List[Id]) = Future {
      println(s"recentlyUpdatedIds: $recentlyUpdatedIds")
    }

    def logSharedUrls(sharedURLs: List[String]) = Future {
      println(s"sharedUrls: $sharedURLs")
    }

    def logRefreshResponses(responses: Set[RefreshResponse]) = Future {
      println(s"twitterRefreshResponses: $responses")
    }
  }
}

