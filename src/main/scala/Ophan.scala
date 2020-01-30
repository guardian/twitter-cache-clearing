package com.gu.socialCacheClearing

import sttp.client._
import sttp.model.Uri
import play.api.libs.json.{JsError, JsSuccess, Json, JsonValidationError, Reads}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend


import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait Ophan[F[_]] {
  def idsToSharedUrls(ids: ::[Id]): F[Set[String]]
}

object Ophan {
  class Production extends Ophan[Future] {
    val apiKey = Credentials.getCredential("ophan/api-key")

    def slashPrefixedPath(capiId: String) = "/" + capiId

    implicit lazy val backend = AsyncHttpClientFutureBackend()

    def twitterReferrals(capiId: String): Future[Seq[ReferralCount]] = {
      implicit val reads: Reads[ReferralCount] = Json.reads[ReferralCount]

      val uri = uri"https://api.ophan.co.uk/api/twitter/referrals?path=${slashPrefixedPath(capiId)}&api-key=$apiKey"

      def referralCounts(response: String): Future[Seq[ReferralCount]] = {
        Json.parse(response).validate[Seq[ReferralCount]] match {
          case JsSuccess(value, _) => Future.successful(value)
          case JsError(errors) => Future.failed(
            //TODO: change this - not sure what we want to log
            JsonParsingError(errors.toSeq.flatMap(_._2))
          )
        }
      }

      def getResponse(uri: Uri): Future[String] = {
        basicRequest.get(uri).send().flatMap { _.body match {
          case Right(res) => Future.successful(res)
          case Left(err) => Future.failed(new Throwable(err))
        }
        }
      }

      for {
        resp <- getResponse(uri)
        referralCounts <- referralCounts(resp)
      } yield referralCounts
    }

    def idsToSharedUrls(ids: ::[com.gu.socialCacheClearing.Id]): Future[SharedURLs] = {
      println(s"Calling Ophan for ids: $ids")
      Future.traverse(ids.toList)(twitterReferrals).map(_.flatten).map(_.map(_.item)).map(_.toSet)
    }

    case class JsonParsingError(errors: Seq[JsonValidationError]) extends Throwable

    case class ReferralCount(item: String, count: Int)
  }
}