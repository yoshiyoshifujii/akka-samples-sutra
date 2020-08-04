package com.github.yoshiyoshifujii.akka.samples.domain

case class ThreadName(value: String) {
  require(value.nonEmpty, "thread name is empty")
}
