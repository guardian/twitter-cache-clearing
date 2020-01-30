package com.gu.socialCacheClearing

import com.danielasfregola.twitter4s.RefreshResponse
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers


class LambdaIntegrationTest extends AnyFlatSpec with Matchers {
  val lambda = new TestLambda

  it should "return a refresh response for an input event set containing recent updates" in {
    val inputEvent = List("recentEvent1")

    //TODO: how could I use contain?
    lambda.run(inputEvent) should be (Set(RefreshResponse("200")))
  }

  it should "not return any responses for an empty input event set" in {
    val inputEvent = List.empty[String]

    lambda.run(inputEvent) should be (Set.empty)
  }

}
