package com.github.yoshiyoshifujii.akka.samples.domain.model

import com.github.yoshiyoshifujii.akka.samples.infrastructure.ulid.ULID

case class AccountId(value: ULID = ULID())
