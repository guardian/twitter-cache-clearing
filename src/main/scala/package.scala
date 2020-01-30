package com.gu

package object socialCacheClearing {
  type Id = String
  type SharedURLs = Set[String]
  type Identity[A] = A
  type Transition[Input, Result] = Input => (Input, Result)
}
