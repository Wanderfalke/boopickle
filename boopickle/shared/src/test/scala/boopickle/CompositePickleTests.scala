package boopickle

import utest._

trait Fruit {
  val weight: Double
  def color: String
}

case class Banana(weight: Double) extends Fruit {
  def color = "yellow"
}

case class Kiwi(weight: Double) extends Fruit {
  def color = "brown"
}

case class Carambola(weight: Double) extends Fruit {
  def color = "yellow"
}

sealed trait Error
case object InvalidName extends Error
case object Unknown extends Error
case object NotFound extends Error

sealed trait Tree
case object Leaf extends Tree
case class Node(value: Int, children:Seq[Tree]) extends Tree

object Tree {
  implicit val treePickler = CompositePickler[Tree]
  treePickler.addConcreteType[Node].addConcreteType[Leaf.type]
}

object CompositePickleTests extends TestSuite {
  override def tests = TestSuite {
    'CaseClassHierarchy {
      implicit val fruitPickler = CompositePickler[Fruit].addConcreteType[Banana].addConcreteType[Kiwi].addConcreteType[Carambola]

      val fruits: Seq[Fruit] = Seq(Kiwi(0.5), Kiwi(0.6), Carambola(5.0), Banana(1.2))
      val bb = Pickle.intoBytes(fruits)
      val u = Unpickle[Seq[Fruit]].fromBytes(bb)
      assert(u == fruits)
    }
    'CaseObjects {
      implicit val errorPickler = CompositePickler[Error].addConcreteType[InvalidName.type].addConcreteType[Unknown.type].addConcreteType[NotFound.type]
      val errors:Map[Error, String] = Map(InvalidName -> "InvalidName", Unknown -> "Unknown", NotFound -> "Not found" )
      val bb = Pickle.intoBytes(errors)
      val u = Unpickle[Map[Error, String]].fromBytes(bb)
      assert(u == errors)
    }
    'Recursive {
      import Tree._
      val tree:Tree = Node(1, Seq(Node(2, Seq(Leaf, Node(3, Seq(Leaf, Leaf)), Node(5, Seq(Leaf, Leaf))))))
      val bb = Pickle.intoBytes(tree)
      val u = Unpickle[Tree].fromBytes(bb)
      assert(u == tree)
    }
  }
}
