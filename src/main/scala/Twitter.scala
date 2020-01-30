package com.gu.socialCacheClearing

import com.danielasfregola.twitter4s.entities.{AccessToken, ConsumerToken}
import com.danielasfregola.twitter4s.{RefreshResponse, TwitterRefreshClient}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait Twitter[F[_]] {
  def refreshSharedUrls(urls: SharedURLs): F[Set[RefreshResponse]]
}

object Twitter {
  object Production {
    val consumerToken = {
      val Array(key,secret) = Credentials.getCredential("twitter/consumer-key").split(':')
      ConsumerToken(key,secret)
    }

    val accessToken = {
      val Array(key,secret) = Credentials.getCredential("twitter/access-token").split(':')
      AccessToken(key,secret)
    }
  }

  class Production(refreshClient: TwitterRefreshClient) extends Twitter[Future] {
    def refreshSharedUrls(urls: SharedURLs): Future[Set[RefreshResponse]] = Future.traverse(urls)(refreshClient.refresh)
  }
}
