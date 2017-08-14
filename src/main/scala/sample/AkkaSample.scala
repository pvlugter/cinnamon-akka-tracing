package sample

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import java.util.concurrent.ThreadLocalRandom.{ current => random }
import scala.concurrent.Await
import scala.concurrent.duration._

object AkkaSample extends App {
  import SampleActor.{ actorRefToTree, Message }

  val node1 = SampleNode("alpha", 25521, SampleActor(1), SampleActor(2), SampleActor(3))
  val node2 = SampleNode("beta", 25522, SampleActor(4), SampleActor(5, error = true))
  val node3 = SampleNode("gamma", 25523, SampleActor(6), SampleActor(7))

  val Seq(actor1, actor2, actor3) = node1.actors
  val Seq(actor4, actor5) = node1.identifyActors(node2)
  val Seq(actor6, actor7) = node1.identifyActors(node3)

  actor1 ! Message("message", Seq(
    actor2 ~> actor1,
    actor3
      ~> (actor4 ~> actor5)
      ~> (actor6 ~> (actor7 ~> actor1))
  ))

  import scala.Console.{ BLUE, RESET }
  println(BLUE + "Press enter to exit..." + RESET)
  System.in.read()

  node1.system.terminate()
  node2.system.terminate()
  node3.system.terminate()
}

object SampleNode {
  case class Create(children: Seq[(String, Props)])
  case class Created(actors: Seq[ActorRef])

  class GuardianActor extends Actor with ActorLogging {
    def receive = {
      case Create(children) =>
        val actors = children map {
          case (name, props) => context.actorOf(props, name)
        }
        sender ! Created(actors)
    }
  }

  def apply(name: String, port: Int, actors: (String, Props)*): SampleNode = {
    val node = new SampleNode(name, port, actors)
    println(s"Started node $name")
    node
  }
}

class SampleNode(val name: String, port: Int, children: Seq[(String, Props)]) {
  import SampleNode.{ Create, Created, GuardianActor }

  val config = ConfigFactory.parseString(s"""
    |akka.remote.artery.canonical.port = $port
    |cinnamon.opentracing.tracer.service-name = "$name"
    """.stripMargin).withFallback(ConfigFactory.load)

  val address = Address("akka", name, "127.0.0.1", port)

  val system = ActorSystem(name, config)
  val guardian = system.actorOf(Props[GuardianActor], name)

  implicit val timeout = Timeout(5.seconds)

  val actorNames = children.map(_._1)

  val actors: Seq[ActorRef] = {
    Await.result(guardian ? Create(children.toSeq), timeout.duration) match {
      case Created(actors) => actors
      case _ => sys.error(s"Couldn't create actors under $guardian")
    }
  }

  def identifyActors(node: SampleNode): Seq[ActorRef] = {
    node.actorNames map { actorName => identify(node, actorName) }
  }

  def identify(node: SampleNode, actorName: String) = {
    Await.result(system.actorSelection(RootActorPath(node.address) / "user" / node.name / actorName) ? Identify(actorName), timeout.duration) match {
      case ActorIdentity(_, Some(ref)) => ref
      case _ => sys.error(s"Couldn't identify $actorName on ${node.address}")
    }
  }
}

object SampleActor {
  import scala.language.implicitConversions

  case class Tree(recipient: ActorRef, next: Seq[Tree]) {
    def ~> (tree: Tree): Tree = copy(next = next :+ tree)
  }

  implicit def actorRefToTree(recipient: ActorRef): Tree = Tree(recipient, Seq.empty)

  case class Message(message: String, next: Seq[Tree])

  val names = IndexedSeq("foo", "bar", "baz", "qux", "quux", "corge", "grault", "garply", "waldo", "fred", "plugh", "xyzzy", "thud")

  def name(n: Int): String = names(n - 1)

  def apply(n: Int, error: Boolean = false): (String, Props) = name(n) -> Props(new SampleActor(error))
}

class SampleActor(error: Boolean = false) extends Actor with ActorLogging {
  import SampleActor.Message

  def receive = {
    case Message(message, next) =>
      delay(min = 10.millis, max = 50.millis)
      if (next.isEmpty) {
        if (error) {
          log.warning("I have a bad feeling about this...")
          delay(min = 5.millis, max = 10.millis)
          log.error("Oh noes!")
          delay(min = 5.millis, max = 10.millis)
          throw new RuntimeException("boom")
        }
        sender ! message
      } else next foreach { tree =>
        tree.recipient ! Message(append(message), tree.next)
        delay(min = 5.millis, max = 10.millis)
      }
      delay(min = 10.millis, max = 50.millis)
  }

  def append(message: String) = s"$message + ${self.path.name}"

  def delay(min: Duration, max: Duration) = Thread.sleep(random.nextLong(min.toMillis, max.toMillis))
}
