package com.danielasfregola.twitter4s

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ContentType, MediaTypes}
import com.danielasfregola.twitter4s
import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}
import play.api.libs.json.Json

import scala.concurrent.Future

case class RefreshResponse(url_resolution_status: String)

class TwitterRefreshClient(consumerToken: ConsumerToken, accessToken: AccessToken)
  (implicit _system: ActorSystem = ActorSystem("twitter4s-rest"))
  extends twitter4s.TwitterRestClient(consumerToken, accessToken) {

  def refresh(url: String): Future[RefreshResponse] = {
    import restClient._

    val jsonOb = Json.obj("url" -> url, "wait" -> "true").toString()
    println(s"Sending Twitter refresh request for url: $url")
    val contentType = ContentType(MediaTypes.`application/json`)
    val request = Post("https://api.twitter.com/1.1/urls/refresh.json", jsonOb, contentType)

    request.respondAs[RefreshResponse]
  }
}
