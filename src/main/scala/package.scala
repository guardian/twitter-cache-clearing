package com.gu

package object socialCacheClearing {
  type Id = String
  type SharedURLs = Set[String]
  type Identity[A] = A
  type Transition[Input, Result] = Input => (Input, Result)

  implicit class FlatMapSyntax[F[_], A](private val fa: F[A])(
    implicit x: Monad[F]) {
    def flatMap[B](f: A => F[B]): F[B] = x.flatMap(fa)(f)
    def map[B](f: A => B): F[B] = x.map(fa)(f)
  }
}
