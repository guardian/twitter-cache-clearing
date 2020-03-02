package com.gu.socialCacheClearing
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait Monad[F[_]] {
  def flatMap[A, B](fa: F[A])(f: A => F[B]): F[B]
  def map[A, B](fa: F[A])(f: A => B): F[B]
  def pure[A](a: A): F[A]
}

object Monad {
  trait MonadFException[E, F[_]] extends Monad[F] {
    def raise[A](e: E): F[A]
  }

  class MonadFutureException(ec: ExecutionContext) extends MonadFException[Throwable, Future] {
    def raise[A](e: Throwable): Future[A] = Future.failed(e)
    def pure[A](a: A): Future[A] = Future.successful(a)
    def map[A, B](fa: Future[A])(f: A => B): Future[B] = fa.map(f)(ec)
    def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)(ec)
  }

  class MonadTryException extends MonadFException[Throwable, Try] {
    def raise[A](e: Throwable): Try[A] = Failure(e)
    def flatMap[A, B](ia: Try[A])(f: A => Try[B]): Try[B] = ia.flatMap(f)
    def map[A, B](ia: Try[A])(f: A => B): Try[B] = ia.map(f)
    def pure[A](a: A): Try[A] = Success(a)
  }

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
