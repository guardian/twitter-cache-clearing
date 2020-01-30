package com.gu.socialCacheClearing

import java.time.Duration.ofMinutes
import java.time.Instant.ofEpochMilli

import com.gu.contentapi.client.model.v1.{Content, ItemResponse}
import com.gu.crier.model.event.v1.{Event, EventPayload, EventType}
import com.gu.contentapi.client.{ContentApiClient, GuardianContentClient}

import scala.math.Ordering.Implicits._
import scala.concurrent.{ExecutionContext, Future}

trait Capi[F[_], CapiEvent] {
  def eventToId: PartialFunction[CapiEvent, Id]

  def eventsToIds(events: List[CapiEvent]): List[Id] = events.collect(eventToId)

  def idsToRecentlyUpdatedIds(ids: ::[Id]): F[List[Id]]
}

object Capi {
  class Production(capiClient: GuardianContentClient)(implicit ec: ExecutionContext)
    extends Capi[Future, Event] {

    def eventToId: PartialFunction[Event, Id] = {
      case Event(_,
      EventType.Update,
      _,
      _,
      Some(EventPayload.Content(content))) =>
        content.id
      case Event(_,
      EventType.RetrievableUpdate,
      _,
      _,
      Some(EventPayload.RetrievableContent(content))) =>
        content.id
    }

    def recentlyUpdated(id: Id): Future[Boolean] = {
      capiClient
        .getResponse(ContentApiClient.item(id))
        .map(_.content.exists(isRecentlyUpdated))
    }

    private def isRecentlyUpdated(content: Content): Boolean = {
      val recencyThreshold = ofMinutes(5)

      val maybeStatus = for {
        webPublicationDate <- content.webPublicationDate
        lastMod <- content.fields.flatMap(_.lastModified)
      } yield ofEpochMilli(webPublicationDate.dateTime).plus(recencyThreshold) > ofEpochMilli(lastMod.dateTime)

      maybeStatus.getOrElse(false) // Is there a nicer way of doing this??
    }

    def idsToRecentlyUpdatedIds(ids: ::[Id]): Future[List[Id]] = {
      Future.traverse(ids.toList)(
        id => recentlyUpdated(id).map((id, _))
      ).map(_.collect {
        case (id, true) => id
      })
    }
  }
}

