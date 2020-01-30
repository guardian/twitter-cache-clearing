package com.gu.socialCacheClearing

import com.amazonaws.services.lambda.runtime.{Context, RequestHandler}
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.danielasfregola.twitter4s.{RefreshResponse, TwitterRefreshClient}
import com.gu.contentapi.client.GuardianContentClient
import com.gu.socialCacheClearing.Twitter.Production.{
  accessToken,
  consumerToken
}

import scala.concurrent.{ExecutionContext, Future}

abstract class Lambda {

  type Event

  def program[F[_], CapiEvent](event: Event)(
      implicit f: Monad[F],
      k: Kinesis[Event, CapiEvent],
      c: Capi[F, CapiEvent],
      o: Ophan[F],
      t: Twitter[F]
      //, l: Logger[F]
  ): F[Set[RefreshResponse]] = {

    val capiEvents = capiEventsFromKinesisEvent(event)

    sealed trait TwitterServiceResponse
    case object TwitterServiceNotCalled extends TwitterServiceResponse
    case class  TwitterServiceRefreshResponse(response: Set[RefreshResponse])

    def  refreshResponseForRecentIds2(recentUpdateIds: ::[Id]): F[Set[RefreshResponse]] = {
      sharedUrlsForContentIds(recentUpdateIds).map(_.toList).flatMap {
        case Nil              => f.pure(Set.empty[RefreshResponse])
        case urls: ::[String] => refreshRequestForSharedUrls(urls.toSet)
      }
    }

    def refreshResponseForRecentIds(
        recentUpdateIds: ::[Id]): F[Set[RefreshResponse]] = {
      sharedUrlsForContentIds(recentUpdateIds).map(_.toList).flatMap {
        case Nil              => f.pure(Set.empty[RefreshResponse])
        case urls: ::[String] => refreshRequestForSharedUrls(urls.toSet)
      }
    }

    def refreshResponseForIds(ids: ::[Id])(
        implicit f: Monad[F]): F[Set[RefreshResponse]] = {
      recentUpdates(ids)
        .flatMap {
        case Nil             => f.pure(Set.empty[RefreshResponse])
        case someIds: ::[Id] => refreshResponseForRecentIds(someIds)
      }
    }

    // could we use a different effect which means we don't need to match on Nil each time?
    // cats? ZIO?
    contentIdsForCapiEvents(capiEvents) match {
      case Nil                => f.pure(Set.empty)
      case contentIds: ::[Id] => refreshResponseForIds(contentIds)
    }
  }

  def capiEventsFromKinesisEvent[Event, CapiEvent](event: Event)(
      implicit kinesis: Kinesis[Event, CapiEvent]): List[CapiEvent] = {
    kinesis.capiEvents(event)
  }

  //TODO: we're defining type F[_] but we don't use it
  def contentIdsForCapiEvents[CapiEvent, F[_]](events: List[CapiEvent])(
      implicit capi: Capi[F, CapiEvent]): List[Id] = {
    capi.eventsToIds(events)
  }

  def recentUpdates[F[_]](ids: ::[Id])(
      implicit capi: Capi[F, _]): F[List[Id]] = {
    capi.idsToRecentlyUpdatedIds(ids)
  }

  def sharedUrlsForContentIds[F[_]](contentIds: ::[Id])(
      implicit ophan: Ophan[F]): F[SharedURLs] = {
    ophan.idsToSharedUrls(contentIds)
  }

  def refreshRequestForSharedUrls[F[_]](sharedUrls: SharedURLs)(
      implicit twitter: Twitter[F]): F[Set[RefreshResponse]] = {
    twitter.refreshSharedUrls(sharedUrls)
  }

  implicit class FlatMapSyntax[F[_], A](private val fa: F[A])(
      implicit x: Monad[F]) {
    def flatMap[B](f: A => F[B]): F[B] = x.flatMap(fa)(f)
    def map[B](f: A => B): F[B] = x.map(fa)(f)
  }

}

//TODO: Use Cats?
trait Monad[F[_]] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def pure[A](a: A): F[A]
}

class FlatMapFuture(ec: ExecutionContext) extends Monad[Future] {
  override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] =
    fa.flatMap(f)(ec)
  override def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)(ec)
  def pure[A](a: A): Future[A] = Future.successful(a)
}

class ProductionLambda extends Lambda with RequestHandler[KinesisEvent, Unit] {
  type Event = KinesisEvent

  val capiClient = new GuardianContentClient(
    Credentials.getCredential("capi-api-key"))
  val ec = scala.concurrent.ExecutionContext.Implicits.global

  implicit val flatMapFuture = new FlatMapFuture(ec)

  implicit val kinesis = Kinesis.Production

  implicit val capi = new Capi.Production(capiClient)(ec)

  implicit val ophan = new Ophan.Production

  val twitterClient = new TwitterRefreshClient(consumerToken, accessToken)
  implicit val twitter = new Twitter.Production(twitterClient)

  override def handleRequest(event: KinesisEvent, context: Context): Unit =
    program(event)

}

class TestLambda extends Lambda {
  type Event = List[String]

  class MonadIdentity extends Monad[Identity] {
    override def flatMap[A, B](ia: Identity[A])(
        f: A => Identity[B]): Identity[B] = f(ia)
    override def map[A, B](ia: Identity[A])(f: A => B): Identity[B] = f(ia)
    def pure[A](a: A): Identity[A] = a
  }

  implicit val monadIdentity = new MonadIdentity

  implicit val kinesis = new Kinesis[Event, String] {
    def capiEvents(event: Event): List[String] = event
  }

  implicit val capi = new Capi[Identity, String] {
    def eventToId: PartialFunction[String, String] = {
      case e => e
    }

    def idsToRecentlyUpdatedIds(ids: ::[String]): Identity[List[String]] = {
      ids.filter(_.startsWith("recent"))
    }
  }

  implicit val ophan = new Ophan[Identity] {
    def idsToSharedUrls(ids: ::[String]): Identity[Set[String]] = {
      val base = "www.theguardian.com/"
      ids.map(id => s"${base + id}?cmp=x").toSet
    }
  }

  implicit val twitter = new Twitter[Identity] {
    def refreshSharedUrls(urls: SharedURLs): Identity[Set[RefreshResponse]] =
      urls.map(_ => RefreshResponse("200"))
  }

  def run(event: Event): Identity[Set[RefreshResponse]] = program(event)
}
