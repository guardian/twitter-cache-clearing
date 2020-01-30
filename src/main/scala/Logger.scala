package com.gu.socialCacheClearing

trait Logger[F[_]] {
  def logMessage(message: String): F[Unit]
}

case class Transition[F](logMessages: List[String])
