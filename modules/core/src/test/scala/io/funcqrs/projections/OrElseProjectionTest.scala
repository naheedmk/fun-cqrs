package io.funcqrs.projections

import io.funcqrs.TestDomainEvent
import org.scalatest.concurrent.{ Futures, ScalaFutures }
import org.scalatest.{ FlatSpec, Matchers, OptionValues }

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrElseProjectionTest extends FlatSpec with Matchers with Futures with ScalaFutures with OptionValues {

  implicit val patienceConf = patienceConfig

  behavior of "OrElseProjection"

  case class FooEvent(value: String) extends TestDomainEvent

  case class BarEvent(num: Int) extends TestDomainEvent

  it should "Events are not propagated to second Projection if first can handle event" in {

    val fooProjection1 = newFooProjection()
    val fooProjection2 = newFooProjection()

    val orElseProjection = fooProjection1 orElse fooProjection2

    whenReady(orElseProjection.onEvent(Envelope(FooEvent("abc"), 1))) { _ =>
      fooProjection1.result.value shouldBe "abc"
      fooProjection2.result shouldBe None
    }
  }

  it should "propagate events to second Projection if first Projection is not defined for passed Event" in {

    val fooProjection = newFooProjection()
    val barProjection = newBarProjection()

    val orElseProjection = fooProjection orElse barProjection

    whenReady(orElseProjection.onEvent(Envelope(BarEvent(10), 1))) { _ =>
      fooProjection.result shouldBe None
      barProjection.result.value shouldBe 10
    }

  }

  it should "stop propagating Event if first Projection fails" in {

    val barProjection = newBarProjection()

    val orElseProjection = newFailingBarProjection() orElse barProjection

    // we must recover it in other to use with ScalaTest
    val recovered = orElseProjection.onEvent(Envelope(BarEvent(10), 1)).recover { case _ => () }

    whenReady(recovered) { _ =>
      barProjection.result shouldBe None
    }
  }

  def newFailingBarProjection() = new Projection {
    def receiveEvent = {
      case Envelope(evt: BarEvent, _) => Future.failed(new IllegalArgumentException("this projection should not receive events"))
    }
  }

  def newFailingFooProjection() = new Projection {
    def receiveEvent = {
      case Envelope(evt: FooEvent, _) => Future.failed(new IllegalArgumentException("this projection should not receive events"))
    }
  }

  class FooProjection extends Projection {
    var result: Option[String] = None

    def receiveEvent = {
      case Envelope(evt: FooEvent, _) =>
        result = Some(evt.value)
        Future.successful(())
    }
  }
  def newFooProjection() = new FooProjection

  class BarProjection extends Projection {
    var result: Option[Int] = None

    def receiveEvent = {
      case Envelope(evt: BarEvent, _) =>
        result = Some(evt.num)
        Future.successful(())
    }
  }

  def newBarProjection() = new BarProjection
}
