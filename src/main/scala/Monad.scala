package com.gu.socialCacheClearing
import scala.concurrent.{ExecutionContext, Future}

trait Monad[F[_]] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def pure[A](a: A): F[A]
}

object Monad {
  class MonadFuture(ec: ExecutionContext) extends Monad[Future] {
    override def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] =
      fa.flatMap(f)(ec)
    override def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)(ec)
    def pure[A](a: A): Future[A] = Future.successful(a)
  }

  class MonadIdentity extends Monad[Identity] {
    override def flatMap[A, B](ia: Identity[A])(
        f: A => Identity[B]): Identity[B] = f(ia)
    override def map[A, B](ia: Identity[A])(f: A => B): Identity[B] = f(ia)
    def pure[A](a: A): Identity[A] = a
  }
}
